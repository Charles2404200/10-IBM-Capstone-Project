import '@testing-library/jest-dom'

// provide ResizeObserver for Carbon components that require it in jsdom
class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

global.ResizeObserver = ResizeObserver