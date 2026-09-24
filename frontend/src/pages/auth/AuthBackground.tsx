/** Landing-style backdrop (dot grid + blurred red glows) shared by the auth screens. Place inside a `relative overflow-hidden` container. */
export const AuthBackground = () => (
  <>
    <div
      className="pointer-events-none absolute inset-0 opacity-55"
      aria-hidden="true"
      style={{
        backgroundImage: 'radial-gradient(circle, #e5e5e5 1px, transparent 1px)',
        backgroundSize: '26px 26px',
        maskImage: 'radial-gradient(60% 55% at 50% 40%, #000 0%, transparent 100%)',
        WebkitMaskImage: 'radial-gradient(60% 55% at 50% 40%, #000 0%, transparent 100%)',
      }}
    />
    <div
      className="pointer-events-none absolute -right-[6%] top-[8%] h-[46rem] w-[46rem] rounded-full bg-[#920703] opacity-[0.12] blur-[90px]"
      aria-hidden="true"
    />
    <div
      className="pointer-events-none absolute -bottom-[8%] -left-[12%] h-[40rem] w-[40rem] rounded-full bg-[#920703] opacity-[0.09] blur-[90px]"
      aria-hidden="true"
    />
  </>
)
