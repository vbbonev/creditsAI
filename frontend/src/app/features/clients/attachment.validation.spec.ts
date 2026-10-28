import { ATTACHMENT_LIMITS } from './attachment.constants';
import { fileIcon, isAllowedType, isPreviewable, validateFiles } from './attachment.validation';

const MB = 1024 * 1024;
const file = (
  name: string,
  size = 100,
  type = '',
): { name: string; size: number; type: string } => ({
  name,
  size,
  type,
});

describe('attachment validation', () => {
  it('should accept allowed types by extension or MIME type', () => {
    expect(isAllowedType(file('a.PDF'))).toBe(true);
    expect(isAllowedType(file('photo.heic'))).toBe(true);
    expect(isAllowedType(file('noext', 1, 'image/png'))).toBe(true);
    expect(isAllowedType(file('virus.exe', 1, 'application/x-msdownload'))).toBe(false);
  });

  it('should accept a valid set', () => {
    expect(validateFiles([file('a.pdf'), file('b.png')])).toEqual([]);
  });

  it('should reject a disallowed type', () => {
    expect(validateFiles([file('a.exe')])[0]).toContain('"a.exe" has a type that is not allowed');
  });

  it('should reject a file over 5 MB but accept exactly 5 MB', () => {
    expect(validateFiles([file('a.pdf', 5 * MB)])).toEqual([]);
    expect(validateFiles([file('a.pdf', 5 * MB + 1)])).toEqual(['"a.pdf" is larger than 5 MB.']);
  });

  it('should reject duplicate names within the selection and against existing files', () => {
    expect(validateFiles([file('a.pdf'), file('A.pdf')])).toEqual([
      'A file named "A.pdf" is already attached.',
    ]);
    expect(validateFiles([file('a.pdf')], [{ fileName: 'a.pdf', sizeBytes: 1 }])).toEqual([
      'A file named "a.pdf" is already attached.',
    ]);
  });

  it('should treat names differing only in case or Unicode composition as duplicates', () => {
    const decomposed = 'A\u0308.pdf'; // "Ä" as A + combining diaeresis (macOS style)
    expect(validateFiles([file(decomposed)], [{ fileName: '\u00e4.pdf', sizeBytes: 1 }])).toEqual([
      `A file named "${decomposed}" is already attached.`,
    ]);
    expect(validateFiles([file('Report.PDF')], [{ fileName: 'report.pdf', sizeBytes: 1 }])).toEqual([
      'A file named "Report.PDF" is already attached.',
    ]);
  });

  it('should reject more than 30 files including existing ones', () => {
    const many = Array.from({ length: ATTACHMENT_LIMITS.maxFiles }, (_, i) => file(`f${i}.txt`));
    expect(validateFiles(many)).toEqual([]);
    expect(validateFiles([...many, file('extra.txt')])).toEqual([
      'A client can have at most 30 files.',
    ]);
    expect(
      validateFiles(
        [file('x.txt')],
        [...many].map((f) => ({ fileName: f.name, sizeBytes: 1 })),
      ),
    ).toEqual(['A client can have at most 30 files.']);
  });

  it('should reject a total over 20 MB, counting existing files', () => {
    const four = [1, 2, 3, 4].map((i) => file(`f${i}.pdf`, 5 * MB));
    expect(validateFiles(four)).toEqual([]);
    expect(
      validateFiles(
        [file('g.pdf', 1)],
        four.map((f) => ({ fileName: f.name, sizeBytes: f.size })),
      ),
    ).toEqual(['All files of a client together can be at most 20 MB.']);
  });

  it('should preview only browser-displayable pictures', () => {
    expect(['image/jpeg', 'image/png', 'image/gif', 'image/webp'].every(isPreviewable)).toBe(true);
    expect(isPreviewable('image/heic')).toBe(false);
    expect(isPreviewable('image/tiff')).toBe(false);
    expect(isPreviewable('application/pdf')).toBe(false);
  });

  it('should pick icons by type', () => {
    expect(fileIcon('image/heic')).toBe('image');
    expect(fileIcon('application/pdf')).toBe('picture_as_pdf');
    expect(fileIcon('text/plain')).toBe('article');
    expect(fileIcon('application/vnd.oasis.opendocument.spreadsheet')).toBe('table_chart');
    expect(fileIcon('application/vnd.oasis.opendocument.text')).toBe('description');
  });
});
