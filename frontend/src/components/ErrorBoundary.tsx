import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/**
 * Catches render/runtime errors in the component tree and shows a recoverable
 * fallback instead of unmounting the whole app to a blank screen.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error:', error, info)
  }

  private handleReload = () => {
    this.setState({ hasError: false })
    window.location.assign('/')
  }

  render() {
    if (!this.state.hasError) return this.props.children

    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 p-6 text-center">
        <h1 className="text-2xl font-semibold text-[var(--color-fate-gold)]">Что-то пошло не так</h1>
        <p className="max-w-md text-sm opacity-70">
          Произошла непредвиденная ошибка. Попробуйте вернуться на главную и повторить действие.
        </p>
        <button
          onClick={this.handleReload}
          className="rounded-md border border-[var(--color-fate-gold)] px-4 py-2 text-sm text-[var(--color-fate-gold)] transition hover:bg-[var(--color-fate-gold)]/10"
        >
          На главную
        </button>
      </div>
    )
  }
}
