import React from 'react'

interface ErrorBoundaryState {
  hasError: boolean
  error?: Error
}

export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  ErrorBoundaryState
> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100vh',
            fontFamily: 'sans-serif',
            color: '#333',
          }}
        >
          <h2 style={{ marginBottom: 8 }}>发生错误</h2>
          <p style={{ color: '#999', fontSize: 14, maxWidth: 400, textAlign: 'center' }}>
            {this.state.error?.message || '未知错误'}
          </p>
          <button
            style={{
              marginTop: 16,
              padding: '6px 16px',
              border: '1px solid #ccc',
              borderRadius: 4,
              background: '#fff',
              cursor: 'pointer',
            }}
            onClick={() => window.location.reload()}
          >
            刷新页面
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
