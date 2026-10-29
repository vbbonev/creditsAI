/** A file attached to a client or a credit, as returned by `GET /api/{clients|credits}/{id}/attachments`. */
export interface Attachment {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  /** ISO-8601 timestamp. */
  uploadedAt: string;
}
