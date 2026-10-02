// The loading text that used to be the whole loading UI now only serves screen readers; sighted
// users get the skeleton instead.
export const LoadingStatus = ({ label }: { label: string }) => (
  <div role="status" className="sr-only">
    {label}
  </div>
)
