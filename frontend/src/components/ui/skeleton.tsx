import * as React from "react"

import { cn } from "@/lib/utils"

// Placeholder block shown while data loads. Decorative (the loading state is announced separately
// by LoadingStatus) and it only pulses when the user has not asked for reduced motion.
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      aria-hidden="true"
      className={cn("rounded-md bg-zinc-200/70 motion-safe:animate-pulse", className)}
      {...props}
    />
  )
}

export { Skeleton }
