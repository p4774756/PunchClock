/** A single-pointer screen-space drag, preserving the grabbed contact offset. */
export class ToolDrag {
  public pointerId: number | null = null;
  public moved = false;
  private startX = 0;
  private startY = 0;
  private offsetX = 0;
  private offsetY = 0;

  begin(id: number, grabX: number, grabY: number, contactX: number, contactY: number): boolean {
    if (this.pointerId !== null || ![id, grabX, grabY, contactX, contactY].every(Number.isFinite)) return false;
    const offsetX = grabX - contactX;
    const offsetY = grabY - contactY;
    if (!Number.isFinite(offsetX) || !Number.isFinite(offsetY)) return false;
    this.pointerId = id;
    this.moved = false;
    this.startX = grabX;
    this.startY = grabY;
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    return true;
  }

  move(id: number, x: number, y: number): {x: number; y: number} | null {
    if (this.pointerId === null || id !== this.pointerId || ![id, x, y].every(Number.isFinite)) return null;
    const contactX = x - this.offsetX;
    const contactY = y - this.offsetY;
    if (!Number.isFinite(contactX) || !Number.isFinite(contactY)) return null;
    // Once a gesture has crossed the threshold, returning to its start remains a drag.
    if (!this.moved && Math.hypot(x - this.startX, y - this.startY) < 3) return null;
    this.moved = true;
    return {x: contactX, y: contactY};
  }

  end(id: number): boolean {
    if (this.pointerId === null || id !== this.pointerId) return false;
    this.cancel();
    return true;
  }

  cancel(): number | null {
    const previous = this.pointerId;
    this.pointerId = null;
    this.moved = false;
    this.startX = this.startY = this.offsetX = this.offsetY = 0;
    return previous;
  }
}
