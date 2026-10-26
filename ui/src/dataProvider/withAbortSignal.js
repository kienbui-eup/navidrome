export const withAbortSignal = (promise, signal) =>
  promise.then((response) => {
    if (signal?.aborted)
      throw new DOMException('List request cancelled', 'AbortError')
    return response
  })
