import { createFileRoute } from '@tanstack/react-router'
import { ChangePasswordForm } from '@/features/auth/components/change-password-form'

export const Route = createFileRoute('/_authenticated/change-password')({
  component: ChangePasswordForm,
})
