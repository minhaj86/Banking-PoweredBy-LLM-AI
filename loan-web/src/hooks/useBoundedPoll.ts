import { useState } from "react";
export function useBoundedPoll() {
  const [start] = useState(() => Date.now());
  return (attempt: number) =>
    document.visibilityState === "hidden" || Date.now() - start > 600000
      ? false
      : Math.min(15000, 2000 + attempt * 750);
}
