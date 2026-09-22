import { useState } from 'react';
import Modal from './Modal';
import Button from './Button';

/**
 * F-15: shown exactly once, right after the Hub's very first activation — the admin password is
 * generated locally and held in memory only (never written to disk, see
 * `FirstRunCredentialHolder`'s javadoc on the backend). If this gets dismissed without copying it
 * down and the Hub process later does a full restart, it's gone for good (there's no
 * "forgot password" flow yet) — the warning copy below says so plainly.
 */
export default function FirstRunCredentialsModal({
  email,
  password,
  onAcknowledge
}: {
  email: string;
  password: string;
  onAcknowledge: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function onCopy() {
    try {
      await navigator.clipboard.writeText(password);
      setCopied(true);
    } catch {
      // clipboard access can fail (permissions); the password is still shown to select manually.
    }
  }

  return (
    <Modal
      title="Contraseña del administrador local"
      footer={
        confirming ? (
          <>
            <Button variant="outline" onClick={() => setConfirming(false)}>Volver</Button>
            <Button variant="primary" onClick={onAcknowledge}>Sí, ya la guardé</Button>
          </>
        ) : (
          <Button variant="primary" onClick={() => setConfirming(true)}>Continuar</Button>
        )
      }
    >
      {confirming ? (
        <p className="text-red-700 font-medium">
          Esta contraseña no se puede volver a mostrar. Si no la guardaste, ciérralo con "Volver" y
          cópiala primero.
        </p>
      ) : (
        <>
          <p>
            Esta es la contraseña del administrador local (<strong>{email}</strong>). Se generó en
            esta máquina y nunca se guarda en un archivo — solo se muestra aquí, una vez.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 rounded-xl border border-border bg-muted px-3 py-2 font-mono text-base select-all">
              {password}
            </code>
            <Button variant="outline" onClick={onCopy}>{copied ? 'Copiada' : 'Copiar'}</Button>
          </div>
          <p className="text-muted-foreground">
            Guárdala ahora e inicia sesión con ella; puedes cambiarla después desde Configuración.
          </p>
        </>
      )}
    </Modal>
  );
}
