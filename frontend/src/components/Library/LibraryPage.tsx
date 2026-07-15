import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Search, FileText } from 'lucide-react';
import { useLibrary } from '../../hooks/useLibrary';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { formatTimestamp } from '../../utils/chatUtils';
import IconButton from '../ui/IconButton';
import PageHeader from '../ui/PageHeader';
import Input from '../ui/Input';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import { Card, CardBody } from '../ui/Card';

const LibraryPage: React.FC = () => {
  useDocumentTitle('Library');
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // Seeds from ?q= — the command palette's "Search docs: <query>" action links here with a query.
  const [searchQuery, setSearchQuery] = useState(() => searchParams.get('q') ?? '');
  const [debouncedQuery, setDebouncedQuery] = useState(() => searchParams.get('q') ?? '');

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedQuery(searchQuery.trim()), 300);
    return () => clearTimeout(handle);
  }, [searchQuery]);

  const { data: documents, isLoading } = useLibrary(debouncedQuery || undefined);

  const groups = useMemo(() => {
    const byProduct = new Map<string, typeof documents>();
    (documents ?? []).forEach(doc => {
      const list = byProduct.get(doc.product) ?? [];
      list.push(doc);
      byProduct.set(doc.product, list as NonNullable<typeof documents>);
    });
    return Array.from(byProduct.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [documents]);

  return (
    <div className="min-h-full bg-background">
      <div className="max-w-4xl mx-auto px-6 py-6">
        <div className="flex items-start gap-3">
          <IconButton label="Go back" variant="ghost" onClick={() => navigate(-1)}>
            <ArrowLeft size={18} />
          </IconButton>
          <div className="flex-1">
            <PageHeader
              title="Document Library"
              description="Everything you can currently ask about."
              actions={<span className="text-sm text-muted-foreground">{documents?.length ?? 0} documents</span>}
            />
          </div>
        </div>

        <div className="relative mb-6">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
          <Input
            type="text"
            aria-label="Search documents"
            placeholder="Search documents…"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="pl-9"
          />
        </div>

        {isLoading ? (
          <div className="space-y-3">
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </div>
        ) : groups.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="No documents found"
            description={debouncedQuery ? 'Try a different search term.' : 'No documents have been ingested yet.'}
          />
        ) : (
          <div className="space-y-6">
            {groups.map(([product, docs]) => (
              <section key={product}>
                <h2 className="text-sm font-semibold text-foreground mb-2">{product}</h2>
                <Card>
                  <CardBody className="p-0 divide-y divide-border">
                    {docs!.map(doc => (
                      <div key={doc.id} className="flex items-center gap-3 px-4 py-3">
                        <FileText size={15} className="text-muted-foreground flex-shrink-0" />
                        <span className="text-sm text-foreground truncate flex-1" title={doc.documentName}>
                          {doc.documentName}
                        </span>
                        <Badge variant="neutral">{doc.version}</Badge>
                        {doc.chunkCount != null && (
                          <span className="text-xs text-muted-foreground whitespace-nowrap">
                            {doc.chunkCount} chunks
                          </span>
                        )}
                        {doc.updatedAt && (
                          <span className="text-xs text-muted-foreground whitespace-nowrap">
                            {formatTimestamp(new Date(doc.updatedAt).getTime())}
                          </span>
                        )}
                      </div>
                    ))}
                  </CardBody>
                </Card>
              </section>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default LibraryPage;
