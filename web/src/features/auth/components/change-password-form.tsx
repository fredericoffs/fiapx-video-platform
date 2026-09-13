import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from '@tanstack/react-router'
import { toast } from 'sonner'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { useSessionStore } from '@/shared/lib/session-store'
import { useChangePasswordMutation } from '@/features/auth/api/mutations'
import { changePasswordSchema, type ChangePasswordFormInput } from '@/features/auth/lib/schemas'

export function ChangePasswordForm() {
  const navigate = useNavigate()
  const session = useSessionStore((state) => state.session)
  const clearMustChangePassword = useSessionStore((state) => state.clearMustChangePassword)
  const changePasswordMutation = useChangePasswordMutation()
  const isForced = session?.mustChangePassword ?? false

  const form = useForm<ChangePasswordFormInput>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmNewPassword: '' },
  })

  const onSubmit = form.handleSubmit((values) => {
    changePasswordMutation.mutate(
      { currentPassword: values.currentPassword, newPassword: values.newPassword },
      {
        onSuccess: () => {
          clearMustChangePassword()
          toast.success('Senha alterada com sucesso')
          void navigate({ to: '/' })
        },
        onError: (error) => {
          toast.error(error.message)
        },
      },
    )
  })

  return (
    <Card className="mx-auto w-full max-w-sm">
      <CardHeader>
        <CardTitle>Trocar senha</CardTitle>
        <CardDescription>
          {isForced
            ? 'Por segurança, você precisa trocar a senha antes de continuar.'
            : 'Defina uma nova senha para a sua conta.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event)
          }}
          noValidate
        >
          <div className="flex flex-col gap-2">
            <Label htmlFor="currentPassword">Senha atual</Label>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              {...form.register('currentPassword')}
            />
            {form.formState.errors.currentPassword && (
              <p className="text-sm text-destructive">
                {form.formState.errors.currentPassword.message}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="newPassword">Nova senha</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              {...form.register('newPassword')}
            />
            {form.formState.errors.newPassword && (
              <p className="text-sm text-destructive">
                {form.formState.errors.newPassword.message}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="confirmNewPassword">Confirmar nova senha</Label>
            <Input
              id="confirmNewPassword"
              type="password"
              autoComplete="new-password"
              {...form.register('confirmNewPassword')}
            />
            {form.formState.errors.confirmNewPassword && (
              <p className="text-sm text-destructive">
                {form.formState.errors.confirmNewPassword.message}
              </p>
            )}
          </div>
          <Button type="submit" disabled={changePasswordMutation.isPending}>
            {changePasswordMutation.isPending ? 'Salvando…' : 'Trocar senha'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
