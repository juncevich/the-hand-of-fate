// Package telemetry wires OpenTelemetry traces, metrics and logs, exported over OTLP/gRPC to the
// collector (OTEL_EXPORTER_OTLP_ENDPOINT). It installs the global tracer/meter/logger providers
// and the W3C trace-context propagator, which the otelgrpc client handler in grpcclient uses to
// continue a command's trace into the backend.
package telemetry

import (
	"context"
	"errors"
	"time"

	"go.opentelemetry.io/contrib/bridges/otelzap"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.43.0"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// ServiceName is the `service.name` resource attribute: Tempo's service, Mimir's `job`, Loki's
// `service_name`.
const ServiceName = "fate-bot"

// scopeName is the instrumentation scope of the bot's own log records.
const scopeName = "github.com/juncevich/the-hand-of-fate/bot"

// metricInterval matches the backend's OTLP step (management.otlp.metrics.export.step).
const metricInterval = 30 * time.Second

// Shutdown flushes pending spans/metrics/logs and stops the exporters.
type Shutdown func(context.Context) error

// Setup installs the global providers exporting to endpoint (e.g. http://otel-collector:4317)
// and returns log extended to also send its records (from its level up) as OTLP logs. With
// disabled set (OTEL_SDK_DISABLED=true) or an empty endpoint it only installs the propagator,
// leaves the no-op providers in place and returns log as it is.
func Setup(ctx context.Context, endpoint string, disabled bool, log *zap.Logger) (Shutdown, *zap.Logger, error) {
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{}, propagation.Baggage{},
	))
	if disabled || endpoint == "" {
		log.Info("telemetry export disabled")
		return func(context.Context) error { return nil }, log, nil
	}

	// Export failures (e.g. no collector during native local dev) are retried by the SDK;
	// keep them out of the normal log level instead of warning every interval — and out of
	// the OTLP logs, whose own export may be what fails.
	otel.SetErrorHandler(otel.ErrorHandlerFunc(func(err error) {
		log.Debug("telemetry export error", zap.Error(err))
	}))

	res, err := resource.Merge(resource.Default(), resource.NewWithAttributes(
		semconv.SchemaURL, semconv.ServiceName(ServiceName),
	))
	if err != nil {
		return nil, nil, err
	}

	// WithEndpointURL takes the scheme into account: http:// means a plaintext connection.
	traceExporter, err := otlptracegrpc.New(ctx, otlptracegrpc.WithEndpointURL(endpoint))
	if err != nil {
		return nil, nil, err
	}
	metricExporter, err := otlpmetricgrpc.New(ctx, otlpmetricgrpc.WithEndpointURL(endpoint))
	if err != nil {
		return nil, nil, errors.Join(err, traceExporter.Shutdown(ctx))
	}
	logExporter, err := otlploggrpc.New(ctx, otlploggrpc.WithEndpointURL(endpoint))
	if err != nil {
		return nil, nil, errors.Join(err, traceExporter.Shutdown(ctx), metricExporter.Shutdown(ctx))
	}

	tracerProvider := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(traceExporter),
		sdktrace.WithResource(res),
	)
	meterProvider := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metricExporter,
			sdkmetric.WithInterval(metricInterval))),
		sdkmetric.WithResource(res),
	)
	loggerProvider := sdklog.NewLoggerProvider(
		sdklog.WithProcessor(sdklog.NewBatchProcessor(logExporter)),
		sdklog.WithResource(res),
	)
	otel.SetTracerProvider(tracerProvider)
	otel.SetMeterProvider(meterProvider)
	otel.SetLoggerProvider(loggerProvider)

	// stdout as before, plus OTLP from the same level up (no debug records unless log has them)
	otlpCore, err := zapcore.NewIncreaseLevelCore(
		otelzap.NewCore(scopeName, otelzap.WithLoggerProvider(loggerProvider)), log.Level(),
	)
	if err != nil {
		return nil, nil, err
	}
	withOTLP := log.WithOptions(zap.WrapCore(func(core zapcore.Core) zapcore.Core {
		return zapcore.NewTee(core, otlpCore)
	}))

	log.Info("telemetry export enabled", zap.String("endpoint", endpoint))
	return func(ctx context.Context) error {
		return errors.Join(tracerProvider.Shutdown(ctx), meterProvider.Shutdown(ctx), loggerProvider.Shutdown(ctx))
	}, withOTLP, nil
}
