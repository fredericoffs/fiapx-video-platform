import { ThemeProvider } from '@/app/theme-provider'
import { Layout } from '@/app/layout'
import { Toaster } from '@/shared/ui/sonner'

function App() {
  return (
    <ThemeProvider>
      <Layout>
        <p className="text-muted-foreground">Sprint 5 em construção.</p>
      </Layout>
      <Toaster />
    </ThemeProvider>
  )
}

export default App
