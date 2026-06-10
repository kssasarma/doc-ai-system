import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../../context/AuthContext';
import { DocumentInfo, IngestionStatus } from '../../types';
import {
  fetchDocuments,
  fetchIngestionStatus,
  uploadDocument,
  retriggerDocument,
} from '../../services/adminService';
import { Upload, RefreshCw, AlertCircle, CheckCircle, Clock, XCircle, ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const STATUS_ICONS: Record<string, React.ReactNode> = {
  COMPLETED: <CheckCircle className="w-4 h-4 text-green-600" />,
  PROCESSING: <Clock className="w-4 h-4 text-blue-500 animate-spin" />,
  PENDING: <Clock className="w-4 h-4 text-yellow-500" />,
  FAILED: <XCircle className="w-4 h-4 text-red-500" />,
};

const STATUS_LABELS: Record<string, string> = {
  COMPLETED: 'text-green-700 bg-green-50',
  PROCESSING: 'text-blue-700 bg-blue-50',
  PENDING: 'text-yellow-700 bg-yellow-50',
  FAILED: 'text-red-700 bg-red-50',
};

export default function AdminPanel() {
  const { token } = useAuth();
  const navigate = useNavigate();

  const [documents, setDocuments] = useState<DocumentInfo[]>([]);
  const [status, setStatus] = useState<IngestionStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // Upload form state
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [product, setProduct] = useState('');
  const [version, setVersion] = useState('');
  const [docName, setDocName] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [uploadSuccess, setUploadSuccess] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadData = useCallback(async () => {
    if (!token) return;
    setError('');
    try {
      const [docs, stat] = await Promise.all([
        fetchDocuments(token),
        fetchIngestionStatus(token),
      ]);
      setDocuments(docs);
      setStatus(stat);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load data');
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadData();
    // Auto-refresh every 10s to pick up processing updates
    const interval = setInterval(loadData, 10000);
    return () => clearInterval(interval);
  }, [loadData]);

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadFile || !token) return;

    setIsUploading(true);
    setUploadError('');
    setUploadSuccess('');

    try {
      await uploadDocument(token, uploadFile, product, version, docName || undefined);
      setUploadSuccess(`"${uploadFile.name}" uploaded and processing started`);
      setUploadFile(null);
      setProduct('');
      setVersion('');
      setDocName('');
      if (fileInputRef.current) fileInputRef.current.value = '';
      loadData();
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setIsUploading(false);
    }
  };

  const handleRetrigger = async (doc: DocumentInfo) => {
    if (!token) return;
    try {
      await retriggerDocument(token, doc.id);
      loadData();
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Retrigger failed');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center gap-4">
        <button
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 text-sm"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Chat
        </button>
        <h1 className="text-xl font-semibold text-gray-900">Admin Panel</h1>
        <button
          onClick={loadData}
          className="ml-auto flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-800"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh
        </button>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-6 space-y-6">
        {/* Status summary */}
        {status && (
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-4">
            {[
              { label: 'Total', value: status.totalDocuments, color: 'text-gray-900' },
              { label: 'Completed', value: status.completed, color: 'text-green-600' },
              { label: 'Processing', value: status.processing, color: 'text-blue-600' },
              { label: 'Failed', value: status.failed, color: 'text-red-600' },
              { label: 'Total Chunks', value: status.totalChunks, color: 'text-purple-600' },
            ].map(({ label, value, color }) => (
              <div key={label} className="bg-white rounded-xl border border-gray-200 p-4 text-center">
                <div className={`text-2xl font-bold ${color}`}>{value}</div>
                <div className="text-xs text-gray-500 mt-1">{label}</div>
              </div>
            ))}
          </div>
        )}

        {/* Upload form */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
            <Upload className="w-5 h-5 text-blue-600" />
            Upload Document
          </h2>
          <form onSubmit={handleUpload} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Product *</label>
                <input
                  type="text"
                  value={product}
                  onChange={e => setProduct(e.target.value)}
                  required
                  placeholder="e.g. case360"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Version *</label>
                <input
                  type="text"
                  value={version}
                  onChange={e => setVersion(e.target.value)}
                  required
                  placeholder="e.g. 23.4"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Document Name (optional)</label>
                <input
                  type="text"
                  value={docName}
                  onChange={e => setDocName(e.target.value)}
                  placeholder="Defaults to filename"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">File * (.pdf or .chm)</label>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,.chm"
                  onChange={e => setUploadFile(e.target.files?.[0] || null)}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 file:mr-3 file:py-1 file:px-2 file:rounded file:border-0 file:text-xs file:bg-blue-50 file:text-blue-700"
                />
              </div>
            </div>

            {uploadError && (
              <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                {uploadError}
              </div>
            )}
            {uploadSuccess && (
              <div className="flex items-center gap-2 text-sm text-green-700 bg-green-50 rounded-lg px-3 py-2">
                <CheckCircle className="w-4 h-4 flex-shrink-0" />
                {uploadSuccess}
              </div>
            )}

            <button
              type="submit"
              disabled={isUploading || !uploadFile}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
            >
              <Upload className="w-4 h-4" />
              {isUploading ? 'Uploading...' : 'Upload & Process'}
            </button>
          </form>
        </div>

        {/* Document list */}
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="text-lg font-semibold text-gray-900">Documents</h2>
          </div>

          {isLoading ? (
            <div className="p-12 text-center text-gray-400">Loading...</div>
          ) : error ? (
            <div className="p-6 text-center text-red-500 flex items-center justify-center gap-2">
              <AlertCircle className="w-5 h-5" />
              {error}
            </div>
          ) : documents.length === 0 ? (
            <div className="p-12 text-center text-gray-400">
              No documents yet. Upload your first document above.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-gray-600 text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-6 py-3 text-left">Document</th>
                    <th className="px-6 py-3 text-left">Product / Version</th>
                    <th className="px-6 py-3 text-left">Status</th>
                    <th className="px-6 py-3 text-right">Chunks</th>
                    <th className="px-6 py-3 text-left">Created</th>
                    <th className="px-6 py-3 text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {documents.map(doc => (
                    <tr key={doc.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4">
                        <div className="font-medium text-gray-900">{doc.documentName}</div>
                        {doc.errorMessage && (
                          <div className="text-xs text-red-500 mt-0.5 max-w-xs truncate" title={doc.errorMessage}>
                            {doc.errorMessage}
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4 text-gray-600">
                        {doc.product} <span className="text-gray-400">v</span>{doc.version}
                      </td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-xs font-medium ${STATUS_LABELS[doc.status] || ''}`}>
                          {STATUS_ICONS[doc.status]}
                          {doc.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right text-gray-600">
                        {doc.chunkCount ?? '—'}
                      </td>
                      <td className="px-6 py-4 text-gray-500 text-xs">
                        {doc.createdAt ? new Date(doc.createdAt).toLocaleString() : '—'}
                      </td>
                      <td className="px-6 py-4 text-center">
                        {(doc.status === 'FAILED' || doc.status === 'PENDING') && (
                          <button
                            onClick={() => handleRetrigger(doc)}
                            title="Retrigger processing"
                            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-orange-700 bg-orange-50 rounded-lg hover:bg-orange-100 transition-colors"
                          >
                            <RefreshCw className="w-3.5 h-3.5" />
                            Retry
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
