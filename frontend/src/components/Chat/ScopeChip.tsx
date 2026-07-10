import React, { Suspense, lazy, useEffect, useRef, useState } from 'react';
import { Filter, X, ChevronDown, GitCompare } from 'lucide-react';
import { ProductEntry } from '../../types';
import { fetchAccessibleProducts } from '../../services/productService';
import { useAuth } from '../../context/AuthContext';

const VersionCompareModal = lazy(() => import('./VersionCompareModal'));

interface ScopeChipProps {
  product?: string;
  version?: string;
  onChange: (product: string | undefined, version: string | undefined) => void;
}

/**
 * Optional, per-conversation retrieval scope — defaults to "everything I have access to" and
 * never blocks sending a message. Pinning a product/version here narrows what's searchable for
 * this chat (see SearchScope.withVersionNarrow on the backend); it never widens it beyond the
 * user's actual access grants.
 */
export default function ScopeChip({ product, version, onChange }: ScopeChipProps) {
  const { token } = useAuth();
  const [open, setOpen] = useState(false);
  const [products, setProducts] = useState<ProductEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [compareTarget, setCompareTarget] = useState<ProductEntry | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || products.length > 0 || !token) return;
    setLoading(true);
    setError(false);
    fetchAccessibleProducts(token)
      .then(setProducts)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [open, token, products.length]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const isPinned = !!(product || version);
  const label = isPinned ? `${product ?? 'Any product'}${version ? ` ${version}` : ''}` : 'All my documents';

  const select = (p: string | undefined, v: string | undefined) => {
    onChange(p, v);
    setOpen(false);
  };

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen(v => !v)}
        className={`flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg transition-colors border flex-shrink-0 ${
          isPinned
            ? 'text-blue-700 bg-blue-50 border-blue-200'
            : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100 border-gray-200'
        }`}
        title="Scope this conversation to a specific product/version"
      >
        <Filter size={14} />
        <span className="max-w-[160px] truncate">{label}</span>
        <ChevronDown size={12} />
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-20 w-64 max-h-80 overflow-y-auto">
          <button
            onClick={() => select(undefined, undefined)}
            className={`w-full text-left px-3 py-2 text-sm hover:bg-gray-50 transition-colors ${
              !isPinned ? 'font-medium text-blue-700' : 'text-gray-700'
            }`}
          >
            All my documents (default)
          </button>
          <div className="border-t border-gray-100 my-1" />
          {loading ? (
            <div className="px-3 py-3 text-xs text-gray-400 text-center">Loading…</div>
          ) : error ? (
            <div className="px-3 py-3 text-xs text-red-500 text-center">Couldn't load products</div>
          ) : products.length === 0 ? (
            <div className="px-3 py-3 text-xs text-gray-400 text-center">No documents available yet</div>
          ) : (
            products.map(p => (
              <div key={p.product}>
                <div className="px-3 py-1 flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide truncate">
                    {p.product}
                  </span>
                  {p.versions.length >= 2 && (
                    <button
                      onClick={() => { setCompareTarget(p); setOpen(false); }}
                      title={`Compare ${p.product} versions`}
                      className="p-0.5 text-gray-400 hover:text-blue-600 flex-shrink-0"
                    >
                      <GitCompare size={12} />
                    </button>
                  )}
                </div>
                <button
                  onClick={() => select(p.product, undefined)}
                  className={`w-full text-left pl-5 pr-3 py-1.5 text-sm hover:bg-gray-50 transition-colors ${
                    product === p.product && !version ? 'font-medium text-blue-700' : 'text-gray-700'
                  }`}
                >
                  All versions
                </button>
                {p.versions.map(v => (
                  <button
                    key={v}
                    onClick={() => select(p.product, v)}
                    className={`w-full text-left pl-5 pr-3 py-1.5 text-sm hover:bg-gray-50 transition-colors ${
                      product === p.product && version === v ? 'font-medium text-blue-700' : 'text-gray-700'
                    }`}
                  >
                    {v}
                  </button>
                ))}
              </div>
            ))
          )}
          {isPinned && (
            <>
              <div className="border-t border-gray-100 my-1" />
              <button
                onClick={() => select(undefined, undefined)}
                className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors flex items-center gap-1.5"
              >
                <X size={13} /> Clear pin
              </button>
            </>
          )}
        </div>
      )}

      {compareTarget && (
        <Suspense fallback={null}>
          <VersionCompareModal
            product={compareTarget.product}
            versions={compareTarget.versions}
            onClose={() => setCompareTarget(null)}
          />
        </Suspense>
      )}
    </div>
  );
}
