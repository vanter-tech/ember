import { useState } from 'react'
import { useTranslation } from '@/lib/i18n'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

const SUPPORT_EMAIL = 'tofernandoband01@outlook.com'

export const ReportIssueForm = ({ onSubmitted }: { onSubmitted?: () => void }) => {
  const { t } = useTranslation('admin')
  const authName = useAuthStore((state) => state.name)
  const [subject, setSubject] = useState('')
  const [sender, setSender] = useState(authName ?? '')
  const [description, setDescription] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const body = `${t('reportIssueSenderLabel')}: ${sender}\n\n${description}`
    const mailto = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`
    window.location.href = mailto
    onSubmitted?.()
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="report-issue-subject">{t('reportIssueSubjectLabel')}</Label>
        <Input
          id="report-issue-subject"
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          placeholder={t('reportIssueSubjectPlaceholder')}
          required
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="report-issue-sender">{t('reportIssueSenderLabel')}</Label>
        <Input
          id="report-issue-sender"
          value={sender}
          onChange={(e) => setSender(e.target.value)}
          placeholder={t('reportIssueSenderPlaceholder')}
          required
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="report-issue-description">{t('reportIssueDescriptionLabel')}</Label>
        <Textarea
          id="report-issue-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('reportIssueDescriptionPlaceholder')}
          rows={6}
          required
        />
      </div>
      <Button type="submit" className="w-full">{t('reportIssueSubmit')}</Button>
      <p className="text-center text-xs text-muted-foreground">{t('reportIssueHint')}</p>
    </form>
  )
}
