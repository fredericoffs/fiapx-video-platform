import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from '@tanstack/react-router'
import { toast } from 'sonner'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { useSessionStore } from '@/shared/lib/session-store'
import { useLoginMutation, useRegisterMutation } from '@/features/auth/api/mutations'
import { registerSchema, type RegisterFormValues } from '@/features/auth/lib/schemas'

export function RegisterForm() {
  const navigate = useNavigate()
  const setSession = useSessionStore((state) => state.setSession)
  const registerMutation = useRegisterMutation()
  const loginMutation = useLoginMutation()

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = form.handleSubmit((values) => {
    registerMutation.mutate(values, {
      onSuccess: () => {
        loginMutation.mutate(values, {
          onSuccess: (data) => {
            setSession({ token: data.accessToken, email: values.email })
            toast.success('Conta criada com sucesso')
            void navigate({ to: '/' })
          },
          onError: () => {
            toast.success('Conta criada — faça login para continuar')
            void navigate({ to: '/login' })
          },
        })
      },
      onError: (error) => {
        toast.error(error.message)
      },
    })
  })

  const isSubmitting = registerMutation.isPending || loginMutation.isPending

  return (
    <Card className="mx-auto w-full max-w-sm">
      <CardHeader>
        <CardTitle>Criar conta</CardTitle>
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
            <Label htmlFor="email">E-mail</Label>
            <Input id="email" type="email" autoComplete="email" {...form.register('email')} />
            {form.formState.errors.email && (
              <p className="text-sm text-destructive">{form.formState.errors.email.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="password">Senha</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              {...form.register('password')}
            />
            {form.formState.errors.password && (
              <p className="text-sm text-destructive">{form.formState.errors.password.message}</p>
            )}
          </div>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Criando conta…' : 'Criar conta'}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-muted-foreground">
          Já tem conta?{' '}
          <Link to="/login" className="text-primary underline-offset-4 hover:underline">
            Entrar
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
