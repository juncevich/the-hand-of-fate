// Package telemetry wires OpenTelemetry traces and metrics, exported over OTLP/gRPC to the
// collector (OTEL_EXPORTER_OTLP_ENDPOINT). It installs the global tracer/meter providers and
// the W3C trace-context propagator, which the otelgrpc client handler in grpcclient uses to
// continue a command's trace into the backend.
package telemetry

import (
	"context"
	"errors"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.43.0"
	"go.uber.org/zap"
)

// ServiceName is the `service.name` resource attribute: Tempo's service, Mimir's `job`.
const ServiceName = "fate-bot"

// metricInterval matches the backend's OTLP step (management.otlp.metrics.export.step).
const metricInterval = 30 * time.Second

// Shutdown flushes pending spans/metrics and stops the exporters.
type Shutdown func(context.Context) error

// Setup installs the global providers exporting to endpoint (e.g. http://otel-collector:4317).
// With disabled set (OTEL_SDK_DISABLED=true) or an empty endpoint it only installs the
// propagator and leaves the no-op providers in place.
func Setup(ctx context.Context, endpoint string, disabled bool, log *zap.Logger) (Shutdown, error) {
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{}, propagation.Baggage{},
	))
	if disabled || endpoint == "" {
		log.Info("telemetry export disabled")
		return func(context.Context) error { return nil }, nil
	}

	// Export failures (e.g. no collector during native local dev) are retried by the SDK;
	// keep them out of the normal log level instead of warning every interval.
	otel.SetErrorHandler(otel.ErrorHandlerFunc(func(err error) {
		log.Debug("telemetry export error", zap.Error(err))
	}))

	res, err := resource.Merge(resource.Default(), resource.NewWithAttributes(
		semconv.SchemaURL, semconv.ServiceName(ServiceName),
	))
	if err != nil {
		return nil, err
	}

	// WithEndpointURL takes the scheme into account: http:// means a plaintext connection.
	traceExporter, err := otlptracegrpc.New(ctx, otlptracegrpc.WithEndpointURL(endpoint))
	if err != nil {
		return nil, err
	}
	metricExporter, err := otlpmetricgrpc.New(ctx, otlpmetricgrpc.WithEndpointURL(endpoint))
	if err != nil {
		return nil, errors.Join(err, traceExporter.Shutdown(ctx))
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
	otel.SetTracerProvider(tracerProvider)
	otel.SetMeterProvider(meterProvider)

	log.Info("telemetry export enabled", zap.String("endpoint", endpoint))
	return func(ctx context.Context) error {
		return errors.Join(tracerProvider.Shutdown(ctx), meterProvider.Shutdown(ctx))
	}, nil
}
