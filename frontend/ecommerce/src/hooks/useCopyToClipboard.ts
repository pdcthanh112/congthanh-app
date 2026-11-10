"use client";
import { useState } from "react";

export interface useCopyToClipboardProps {
  timeout?: number;
}

export function useCopyToClipboard({ timeout = 2000 }: useCopyToClipboardProps) {
  const [isCopied, setIsCopied] = useState<Boolean>(false);

  const copyToClipboard = (value: string) => {
    if (typeof window === "undefined" || !navigator.clipboard?.writeText) {
      return;
    }

    if (!value) {
      return;
    }

    navigator.clipboard.writeText(value).then(() => {
      setIsCopied(true);

      setTimeout(() => {
        setIsCopied(false);
      }, timeout);
    });
  };

  return { isCopied, copyToClipboard };
}

// cach dung:
// import useCopyToClipboard from "./useCopyToClipboard"

// export default function CopyToClipboardComponent() {
//     const [copyToClipboard, { success }] = useCopyToClipboard({timeout: 3000})

//     return (
//         <>
//             <button onClick={() => copyToClipboard("This was copied")}>
//                 {success ? "Copied" : "Copy Text"}
//             </button>
//             <input type="text" />
//         </>
//     )
// }
