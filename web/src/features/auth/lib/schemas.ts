import { z } from 'zod'

export const loginSchema = z.object({
  email: z.email('E-mail inválido'),
  password: z.string().min(1, 'Informe a senha'),
})

export type LoginFormValues = z.infer<typeof loginSchema>

export const registerSchema = z.object({
  email: z.email('E-mail inválido'),
  password: z
    .string()
    .min(8, 'A senha precisa ter no mínimo 8 caracteres')
    .max(100, 'A senha pode ter no máximo 100 caracteres'),
})

export type RegisterFormValues = z.infer<typeof registerSchema>

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Informe a senha atual'),
    newPassword: z
      .string()
      .min(8, 'A nova senha precisa ter no mínimo 8 caracteres')
      .max(100, 'A nova senha pode ter no máximo 100 caracteres'),
    confirmNewPassword: z.string().min(1, 'Confirme a nova senha'),
  })
  .refine((values) => values.newPassword === values.confirmNewPassword, {
    message: 'As senhas não coincidem',
    path: ['confirmNewPassword'],
  })

export type ChangePasswordFormInput = z.infer<typeof changePasswordSchema>
export type ChangePasswordFormValues = Omit<ChangePasswordFormInput, 'confirmNewPassword'>
