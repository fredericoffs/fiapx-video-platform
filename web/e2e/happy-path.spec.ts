import path from 'node:path'
import { expect, test } from '@playwright/test'

const FIXTURE_PATH = path.join(import.meta.dirname, 'fixtures/sample.mp4')

function uniqueEmail(): string {
  const timestamp = Date.now().toString()
  const random = Math.floor(Math.random() * 10_000).toString()
  return `e2e-${timestamp}-${random}@example.com`
}

test('registro, upload e download do zip de frames', async ({ page }) => {
  const email = uniqueEmail()
  const password = 'senha-e2e-123'

  await page.goto('/register')
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(password)
  await page.getByRole('button', { name: /criar conta/i }).click()

  await expect(page.getByRole('heading', { name: 'Meus vídeos' })).toBeVisible()

  await page.locator('input[type="file"]').setInputFiles(FIXTURE_PATH)

  const row = page.getByRole('listitem').filter({ hasText: 'sample.mp4' })
  await expect(row).toBeVisible()
  await expect(row.getByText(/na fila|processando/i)).toBeVisible()

  await expect(row.getByText('Concluído')).toBeVisible({ timeout: 60_000 })

  const downloadPromise = page.waitForEvent('download')
  await row.getByRole('button', { name: /baixar zip de frames/i }).click()
  const download = await downloadPromise

  expect(download.suggestedFilename()).toContain('sample')
})
