import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/')({
  component: DashboardPage,
})

function DashboardPage() {
  return <h1 className="text-2xl font-semibold">Meus vídeos</h1>
}
