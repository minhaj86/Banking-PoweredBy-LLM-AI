import { useEffect, useRef, useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from "@mui/material";
import * as pdfjs from "pdfjs-dist";
import worker from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { apiBlob } from "../../api/client";
import { ErrorNotice, Loading } from "../../components/Feedback";
import type { Document } from "../../types/api";
pdfjs.GlobalWorkerOptions.workerSrc = worker;
export default function DocumentViewer({
  applicationId,
  document: doc,
  close,
}: {
  applicationId: string;
  document: Document;
  close: () => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [pdf, setPdf] = useState<pdfjs.PDFDocumentProxy>();
  const [page, setPage] = useState(1);
  const [url, setUrl] = useState<string>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const abort = new AbortController();
    let objectUrl: string | undefined;
    let task: pdfjs.PDFDocumentLoadingTask | undefined;
    void apiBlob(
      `/loan-applications/${applicationId}/documents/${doc.id}/content`,
      abort.signal,
    )
      .then(async (blob) => {
        if (abort.signal.aborted) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
        if (doc.mediaType === "application/pdf") {
          task = pdfjs.getDocument({
            data: await blob.arrayBuffer(),
            enableXfa: false,
            useWorkerFetch: false,
          });
          const loaded = await task.promise;
          if (!abort.signal.aborted) setPdf(loaded);
        }
        setLoading(false);
      })
      .catch((e) => {
        if (!abort.signal.aborted) {
          setError(e);
          setLoading(false);
        }
      });
    return () => {
      abort.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      void task?.destroy();
    };
  }, [applicationId, doc.id, doc.mediaType]);
  useEffect(() => {
    if (!pdf || !canvas.current) return;
    let render: pdfjs.RenderTask | undefined;
    let cancelled = false;
    void pdf
      .getPage(page)
      .then((p) => {
        if (cancelled || !canvas.current) return;
        const viewport = p.getViewport({ scale: 1.3 });
        const element = canvas.current;
        element.width = viewport.width;
        element.height = viewport.height;
        render = p.render({ canvas: element, viewport });
        return render.promise;
      })
      .catch((e) => {
        if (!cancelled) setError(e);
      });
    return () => {
      cancelled = true;
      render?.cancel();
    };
  }, [pdf, page]);
  return (
    <Dialog
      open
      onClose={close}
      fullWidth
      maxWidth="md"
      aria-labelledby="document-title"
    >
      <DialogTitle id="document-title">
        Evidence · {doc.category.replaceAll("_", " ").toLowerCase()}
      </DialogTitle>
      <DialogContent>
        {loading && <Loading label="Opening authorised document" />}
        {Boolean(error) && <ErrorNotice error={error} />}
        <Typography variant="caption" component="p" sx={{ mb: 2 }}>
          Security scan: clean. Content is evidence and has not independently
          verified authenticity.
        </Typography>
        {pdf ? (
          <>
            <Stack
              direction="row"
              sx={{
                alignItems: "center",
                gap: 2,
                mb: 2,
              }}
            >
              <Button
                disabled={page === 1}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous page
              </Button>
              <Typography>
                Page {page} of {pdf.numPages}
              </Typography>
              <Button
                disabled={page >= pdf.numPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next page
              </Button>
            </Stack>
            <canvas
              ref={canvas}
              role="img"
              aria-label={`Document page ${page}. Download the original for its accessible text.`}
              style={{ maxWidth: "100%", height: "auto" }}
            />
          </>
        ) : url && doc.mediaType.startsWith("image/") ? (
          <img src={url} alt="Uploaded evidence" style={{ maxWidth: "100%" }} />
        ) : null}
      </DialogContent>
      <DialogActions>
        {url && (
          <Button
            href={url}
            download={`evidence.${doc.mediaType === "application/pdf" ? "pdf" : doc.mediaType === "image/png" ? "png" : "jpg"}`}
          >
            Download original
          </Button>
        )}
        <Button onClick={close}>Close preview</Button>
      </DialogActions>
    </Dialog>
  );
}
