import React, { useState } from 'react';

interface AvatarProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

export const Avatar = ({ children, className = '', ...props }: AvatarProps) => {
  return (
    <div
      className={`relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};

interface AvatarImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {}

export const AvatarImage = ({ src, alt, className = '', ...props }: AvatarImageProps) => {
  const [hasError, setHasError] = useState(false);

  if (hasError || !src) {
    return null;
  }

  return (
    <img
      src={src}
      alt={alt}
      className={`aspect-square h-full w-full object-cover ${className}`}
      onError={() => setHasError(true)}
      {...props}
    />
  );
};

interface AvatarFallbackProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

export const AvatarFallback = ({ children, className = '', ...props }: AvatarFallbackProps) => {
  return (
    <div
      className={`flex h-full w-full items-center justify-center rounded-full bg-gray-100 text-gray-600 font-medium text-sm ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};