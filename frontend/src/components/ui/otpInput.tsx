import React, { useState, useRef, type KeyboardEvent, type ClipboardEvent, useEffect } from 'react';

interface OtpInputProps {
  length?: number;
  value: string;
  onChange: (value: string) => void;
}

export const OtpInput = ({ length = 5, value, onChange }: OtpInputProps) => {
  // Inicializamos un arreglo con la longitud deseada (5 por defecto)
  const [otp, setOtp] = useState<string[]>(Array(length).fill(''));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Sincronizar estado interno si el valor cambia desde afuera (ej. limpiar el input)
  useEffect(() => {
    if (!value) {
      setOtp(Array(length).fill(''));
    }
  }, [value, length]);

  const focusInput = (index: number) => {
    if (index >= 0 && index < length) {
      inputRefs.current[index]?.focus();
    }
  };

  const handleChange = (index: number, e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    
    // Solo permitimos letras y números
    if (!/^[0-9A-Za-z]*$/.test(val)) return; 

    const newOtp = [...otp];
    // Tomamos solo el último carácter digitado y lo forzamos a mayúscula
    newOtp[index] = val.substring(val.length - 1).toUpperCase();
    
    setOtp(newOtp);
    onChange(newOtp.join(''));

    // Mover el foco al siguiente cuadro automáticamente
    if (val && index < length - 1) {
      focusInput(index + 1);
    }
  };

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      // Si la casilla está vacía y presionan borrar, retrocede a la casilla anterior
      focusInput(index - 1);
    }
  };

  const handlePaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData('text').toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, length);
    
    if (pastedData) {
      const newOtp = [...otp];
      for (let i = 0; i < pastedData.length; i++) {
        newOtp[i] = pastedData[i];
      }
      setOtp(newOtp);
      onChange(newOtp.join(''));
      
      // Mover el foco a la última casilla llenada
      focusInput(Math.min(pastedData.length, length - 1));
    }
  };

  return (
    <div className="flex justify-center gap-2 sm:gap-4 w-full my-6">
      {otp.map((digit, index) => (
        <input
          key={index}
          ref={(el) => {inputRefs.current[index] = el}}
          type="text"
          inputMode="text"
          maxLength={1}
          value={digit}
          onChange={(e) => handleChange(index, e)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={handlePaste}
          className="w-12 h-14 sm:w-14 sm:h-16 text-center text-2xl font-bold text-gray-900 bg-white border-2 border-gray-200 rounded-xl focus:border-[#8c1717] focus:ring-4 focus:ring-[#8c1717]/10 outline-none transition-all uppercase"
        />
      ))}
    </div>
  );
};