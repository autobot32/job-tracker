import React, { useEffect, useMemo, useState } from "react";

const STATUS_STYLES: Record<string, string> = {
  applied: "bg-blue-100 text-blue-800",
  assessment: "bg-sky-100 text-sky-800",
  interview: "bg-amber-100 text-amber-900",
  offer: "bg-emerald-100 text-emerald-900",
  rejected: "bg-rose-100 text-rose-900",
  other: "bg-gray-100 text-gray-800",
};

export type ApplicationRow = {
  id: string;
  company?: string;
  roleTitle?: string;
  location?: string;
  status?: string;
  lastUpdatedAt?: string;
};

const fetchApplications = async (): Promise<ApplicationRow[]> => {
  const res = await fetch("/api/applications", { credentials: "include" }); // ✅ fixed path
  if (!res.ok) throw new Error(`Failed to fetch: ${res.status}`);
  return res.json();
};

function classNames(...s: Array<string | false | undefined>) {
  return s.filter(Boolean).join(" ");
}
function toLocalDate(ts?: string) {
  if (!ts) return "—";
  try {
    const d = new Date(ts);
    return d.toLocaleString();
  } catch {
    return ts;
  }
}

export default function ApplicationsDashboard() {
  const [data, setData] = useState<ApplicationRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters & UI state
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<string | "all">("all");
  const [sortBy, setSortBy] = useState<"updated" | "company" | "status">(
    "updated"
  );

  useEffect(() => {
    (async () => {
      try {
        setLoading(true);
        const rows = await fetchApplications();
        setData(rows);
      } catch (e: any) {
        setError(e.message ?? "Failed to load");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let rows = data.filter((r) => {
      const matchesQuery = !q
        ? true
        : [r.company, r.roleTitle, r.location]
            .filter(Boolean)
            .some((v) => String(v).toLowerCase().includes(q));
      const matchesStatus =
        status === "all" ? true : (r.status ?? "other") === status;
      return matchesQuery && matchesStatus;
    });

    rows = rows.sort((a, b) => {
      if (sortBy === "company") {
        return (a.company ?? "").localeCompare(b.company ?? "");
      }
      if (sortBy === "status") {
        return (a.status ?? "").localeCompare(b.status ?? "");
      }
      const at = a.lastUpdatedAt ? new Date(a.lastUpdatedAt).getTime() : 0;
      const bt = b.lastUpdatedAt ? new Date(b.lastUpdatedAt).getTime() : 0;
      return bt - at;
    });

    return rows;
  }, [data, query, status, sortBy]);

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="sticky top-0 z-10 bg-white/80 backdrop-blur supports-[backdrop-filter]:bg-white/60 border-b border-gray-100">
        <div className="mx-auto max-w-6xl px-4 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold">Applications</h1>
            <p className="text-sm text-gray-500">
              Email → Parser → Database → Dashboard
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => downloadCSV(filtered)}
              className="px-3 py-2 rounded-xl bg-white ring-1 ring-gray-200 shadow-sm hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
            >
              Export CSV
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <div className="grid md:grid-cols-4 gap-3 mb-4">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search company, role, location…"
            className="md:col-span-2 w-full rounded-xl bg-white ring-1 ring-gray-200 shadow-sm px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as any)}
            className="w-full rounded-xl bg-white ring-1 ring-gray-200 shadow-sm px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="all">All statuses</option>
            {[
              "applied",
              "assessment",
              "interview",
              "offer",
              "rejected",
              "other",
            ].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>

          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as any)}
            className="w-full rounded-xl bg-white ring-1 ring-gray-200 shadow-sm px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="updated">Sort by: Last updated</option>
            <option value="company">Sort by: Company</option>
            <option value="status">Sort by: Status</option>
          </select>
        </div>

        {/* Summary cards (no dark borders; soft ring) */}
        <SummaryRow rows={filtered} />

        <div className="mt-4 overflow-x-auto rounded-2xl bg-white ring-1 ring-gray-200 shadow-sm">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr className="text-left text-gray-500">
                <Th>Company</Th>
                <Th>Role</Th>
                <Th>Location</Th>
                <Th>Status</Th>
                <Th>Updated</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-gray-500">
                    Loading…
                  </td>
                </tr>
              )}
              {error && !loading && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-rose-600">
                    {error}
                  </td>
                </tr>
              )}
              {!loading && !error && filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-gray-500">
                    No applications match your filters.
                  </td>
                </tr>
              )}
              {!loading &&
                !error &&
                filtered.map((r) => (
                  <tr key={r.id} className="hover:bg-gray-50/70">
                    <Td className="font-medium">{r.company ?? "(unknown)"}</Td>
                    <Td>{r.roleTitle ?? "(unknown)"}</Td>
                    <Td>{r.location ?? "—"}</Td>
                    <Td>
                      <span
                        className={classNames(
                          "inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium",
                          STATUS_STYLES[(r.status ?? "other").toLowerCase()] ||
                            STATUS_STYLES.other
                        )}
                        title={r.status ?? "other"}
                      >
                        {r.status ?? "other"}
                      </span>
                    </Td>
                    <Td>{toLocalDate(r.lastUpdatedAt)}</Td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide">
      {children}
    </th>
  );
}
function Td({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <td
      className={classNames(
        "px-4 py-3 align-top text-sm text-gray-800",
        className
      )}
    >
      {children}
    </td>
  );
}

function SummaryRow({ rows }: { rows: ApplicationRow[] }) {
  const total = rows.length;
  const byStatus = useMemo(() => {
    const map: Record<string, number> = {};
    for (const r of rows) {
      const s = (r.status ?? "other").toLowerCase();
      map[s] = (map[s] ?? 0) + 1;
    }
    return map;
  }, [rows]);

  const chip = (label: string, count?: number, color = "bg-white") => (
    <div
      className={classNames(
        "rounded-2xl px-4 py-3 shadow-sm ring-1 ring-gray-200",
        color
      )}
    >
      <div className="text-xs uppercase tracking-wide text-gray-600">
        {label}
      </div>
      <div className="text-2xl font-semibold">{count ?? 0}</div>
    </div>
  );

  return (
    <div className="grid grid-cols-2 md:grid-cols-6 gap-3">
      {chip("Total", total)}
      {chip("Applied", byStatus.applied, "bg-blue-50")}
      {chip("Assessment", byStatus.assessment, "bg-sky-50")}
      {chip("Interview", byStatus.interview, "bg-amber-50")}
      {chip("Offer", byStatus.offer, "bg-emerald-50")}
      {chip("Rejected", byStatus.rejected, "bg-rose-50")}
    </div>
  );
}

function downloadCSV(rows: ApplicationRow[]) {
  const headers = [
    "company",
    "roleTitle",
    "location",
    "status",
    "lastUpdatedAt",
  ];
  const escape = (v: any) => `"${String(v ?? "").replaceAll('"', '""')}"`;
  const lines = [headers.join(",")].concat(
    rows.map((r) =>
      [r.company, r.roleTitle, r.location, r.status, r.lastUpdatedAt]
        .map(escape)
        .join(",")
    )
  );
  const blob = new Blob([lines.join("\n")], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `applications-export-${new Date()
    .toISOString()
    .slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
