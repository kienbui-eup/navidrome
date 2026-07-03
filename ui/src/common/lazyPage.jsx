import React, { Suspense } from 'react'
import { Loading } from 'react-admin'

// Small error boundary so a failed dynamic-import (e.g. a network hiccup while
// fetching a code-split chunk) degrades to a simple message instead of a blank
// white screen.
class ChunkErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return <div style={{ padding: '2em' }}>Failed to load this page.</div>
    }
    return this.props.children
  }
}

// Wrap a `() => import('./Foo')` into a component that code-splits Foo behind a
// Suspense + error boundary. The returned component is self-contained, so it can
// be passed directly as a react-admin Resource `list`/`show` prop without the
// caller having to provide its own Suspense boundary.
export const lazyPage = (importFn) => {
  const LazyComponent = React.lazy(importFn)
  const Wrapped = (props) => (
    <ChunkErrorBoundary>
      <Suspense fallback={<Loading />}>
        <LazyComponent {...props} />
      </Suspense>
    </ChunkErrorBoundary>
  )
  Wrapped.displayName = 'LazyPage'
  return Wrapped
}

export default lazyPage
