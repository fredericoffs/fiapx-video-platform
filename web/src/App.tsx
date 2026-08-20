import { RouterProvider } from '@tanstack/react-router'
import { Providers } from '@/app/providers'
import { router } from '@/app/router'

function App() {
  return (
    <Providers>
      <RouterProvider router={router} />
    </Providers>
  )
}

export default App
