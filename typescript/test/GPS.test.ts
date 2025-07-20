import { expect } from 'chai';
import { GPC } from '../src/GPC';

describe('GPC Tests', () => {

  it('should encode and decode zero', () => {
    expect(GPC.encode(0, 0)).to.equal('#DCCC-CCCC-CCC');
    expect(GPC.decode('#DCCC-CCCC-CCC')).to.deep.equal([0, 0]);
  });

  it('should encode and decode minimum positive values', () => {
    expect(GPC.encode(0.00001, 0.00001)).to.equal('#DCCC-CCCC-CCR');
    expect(GPC.decode('#DCCC-CCCC-CCR')).to.deep.equal([0.00001, 0.00001]);
  });

  it('should encode and decode -lat/+long', () => {
    expect(GPC.encode(-0.00001, 0.00001)).to.equal('#DCCD-7Y5W-LLH');
    expect(GPC.decode('#DCCD-7Y5W-LLH')).to.deep.equal([-0.00001, 0.00001]);
  });

  it('should encode and decode +lat/-long', () => {
    expect(GPC.encode(0.00001, -0.00001)).to.equal('#DCCC-8473-0G4');
    expect(GPC.decode('#DCCC-8473-0G4')).to.deep.equal([0.00001, -0.00001]);
  });

  it('should encode and decode -lat/-long', () => {
    expect(GPC.encode(-0.00001, -0.00001)).to.equal('#DCCG-5K1D-WV7');
    expect(GPC.decode('#DCCG-5K1D-WV7')).to.deep.equal([-0.00001, -0.00001]);
  });

  it('should encode and decode max values', () => {
    expect(GPC.encode(89.99999, 179.99999)).to.equal('#HG9K-PCVH-DPV');
    expect(GPC.decode('#HG9K-PCVH-DPV')).to.deep.equal([89.99999, 179.99999]);
  });

  it('should throw for invalid latitude', () => {
    [-90, 90].forEach(lat => {
      expect(() => GPC.encode(lat, 123)).to.throw('LATITUDE: value out of valid range.');
    });
  });

  it('should throw for invalid longitude', () => {
    [-180, 180].forEach(long => {
      expect(() => GPC.encode(12, long)).to.throw('LONGITUDE: value out of valid range.');
    });
  });

  it('should support formatted GPC', () => {
    expect(GPC.encode(-89.99999, -179.99999, true)).to.equal('#HG9P-JLHJ-X69');
    expect(GPC.decode('#HG9P-JLHJ-X69')).to.deep.equal([-89.99999, -179.99999]);
  });

  it('should support unformatted GPC', () => {
    expect(GPC.encode(-89.99999, -179.99999, false)).to.equal('HG9PJLHJX69');
    expect(GPC.decode('HG9PJLHJX69')).to.deep.equal([-89.99999, -179.99999]);
  });

  it('should throw for null/empty GPC', () => {
    ['', '   ', null].forEach((code: any) => {
      expect(() => GPC.decode(code)).to.throw('GPC_NULL: Invalid GPC.');
    });
  });

  it('should throw for bad GPC length', () => {
    ['#HG9P-JLHJ-X696', '#HG9P-JLHJ-X6'].forEach(code => {
      expect(() => GPC.decode(code)).to.throw('GPC_LENGTH: Invalid GPC.');
    });
  });

  it('should throw for invalid GPC characters', () => {
    ['#HG9P-JLHJ-A69', '#HG9P-JLHJ-E69'].forEach(code => {
      expect(() => GPC.decode(code)).to.throw('GPC_CHAR: Invalid GPC.');
    });
  });

  it('should throw for valid format but out-of-range GPCs', () => {
    ['#HG9P-JLHJ-X7C', '#JG9P-JLHJ-X7C'].forEach(code => {
      expect(() => GPC.decode(code)).to.throw('GPC_RANGE: Invalid GPC.');
    });
  });

});
