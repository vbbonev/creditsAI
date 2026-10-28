import { FileSizePipe, formatFileSize } from './file-size.pipe';

describe('FileSizePipe', () => {
  it('should format bytes, kilobytes and megabytes', () => {
    expect(formatFileSize(0)).toBe('0 B');
    expect(formatFileSize(1023)).toBe('1023 B');
    expect(formatFileSize(1536)).toBe('1.5 KB');
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5 MB');
  });

  it('should work as a pipe', () => {
    expect(new FileSizePipe().transform(2048)).toBe('2 KB');
  });
});
