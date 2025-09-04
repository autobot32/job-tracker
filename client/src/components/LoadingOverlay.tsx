import React from "react";

export default function LoadingOverlay({
  text = "Working...",
}: {
  text?: string;
}) {
  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-white/70 backdrop-blur-sm">
      <div className="flex flex-col items-center gap-3">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-gray-300 border-t-gray-700" />
        <div className="text-sm text-gray-700">{text}</div>
      </div>
    </div>
  );
}
