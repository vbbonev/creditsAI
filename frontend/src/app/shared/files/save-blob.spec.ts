import { saveBlob } from './save-blob';

describe('saveBlob', () => {
  it('should click a download anchor with the file name and revoke the URL', () => {
    const createUrl = vi.fn().mockReturnValue('blob:x');
    const revokeUrl = vi.fn();
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl }),
    );
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('a.pdf');
      expect(this.href).toBe('blob:x');
    });
    const blob = new Blob(['x']);
    saveBlob(blob, 'a.pdf');
    expect(createUrl).toHaveBeenCalledWith(blob);
    expect(click).toHaveBeenCalledTimes(1);
    expect(revokeUrl).toHaveBeenCalledWith('blob:x');
    click.mockRestore();
  });
});
