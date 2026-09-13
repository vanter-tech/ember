import { useEffect, useState } from 'react';
import { Printer } from 'lucide-react';
import { getPrinters, testPrint } from '../lib/api';
import type { DiscoveredPrinter } from '../lib/types';
import Card from './Card';

export default function PrintersSection() {
  const [printers, setPrinters] = useState<DiscoveredPrinter[]>([]);
  const [selected, setSelected] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  async function refresh() {
    const list = await getPrinters();
    setPrinters(list);
    if (!selected && list.length > 0) {
      setSelected(list[0].name);
    }
  }

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, 5000);
    return () => clearInterval(id);
  }, []);

  async function onTestPrint() {
    if (!selected) {
      setMessage('No hay ninguna cola seleccionada.');
      return;
    }
    try {
      const res = await testPrint(selected);
      setMessage(res.message);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Error desconocido');
    }
  }

  return (
    <Card icon={Printer} title="Impresoras conectadas">
      <div className="flex gap-2 mb-2">
        <select
          className="border border-border rounded-md px-2 py-1 flex-1 min-w-0"
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
        >
          {printers.map((p) => (
            <option key={p.name} value={p.name}>{p.name}</option>
          ))}
        </select>
        <button className="border border-border rounded-md px-3 py-1 shrink-0" onClick={refresh}>
          Actualizar
        </button>
      </div>
      <button
        className="bg-primary text-primary-foreground rounded-md px-4 py-1.5 w-fit"
        onClick={onTestPrint}
      >
        Imprimir página de prueba
      </button>
      {message && <p className="text-sm mt-2">{message}</p>}
    </Card>
  );
}
