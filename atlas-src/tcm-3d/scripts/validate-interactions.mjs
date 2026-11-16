import assert from 'node:assert/strict';
import {PointerTap} from '../app/pointer-tap.ts';

const tap=new PointerTap();
tap.down(1,10,10,5);assert.equal(tap.up(1,12,11),true);
tap.down(1,10,10,5);tap.move(1,40,10);assert.equal(tap.up(1,10,10),false);
tap.down(1,10,10,12);tap.down(2,20,20,12);assert.equal(tap.up(2,20,20),false);assert.equal(tap.up(1,10,10),false);
tap.down(1,10,10,5);tap.cancel(1);assert.equal(tap.up(1,10,10),false);
tap.down(1,10,10,5);assert.equal(tap.up(1,10,10),true);
console.log('Tap, drag, multitouch, cancellation checks passed (anatomy packing removed).');
