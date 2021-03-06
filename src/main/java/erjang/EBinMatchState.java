/**
 * This file is part of Erjang - A JVM-based Erlang VM
 *
 * Copyright (c) 2009 by Trifork
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package erjang;

import java.math.BigInteger;
import java.nio.ByteOrder;

/** Match state is another one of those pseudo-terms that are allowed on the stack, 
 * but in a very controlled fashion. */
public class EBinMatchState extends EPseudoTerm {

	public EBinMatchState testBinMatchState() {
		return this;
	}

	/** Field is guaranteed to be byte-aligned. */
	public static final int BSF_ALIGNED = 1;	
	
	/** Field is little-endian (otherwise big-endian). */
	public static final int BSF_LITTLE = 2;		
	
	/** Field is signed (otherwise unsigned). */
	public static final int BSF_SIGNED = 4;		
	
	/** Size in bs_init is exact. */
	public static final int BSF_EXACT = 8;		

	/** Native endian. */
	public static final int BSF_NATIVE = 16;		

	public static final EAtom ATOM_ALL = EAtom.intern("all");

	public final EBitString bin;
	long start_offset;
	long offset;
	long[] save_offset;

	public long bitsLeft() {
		return bin.bitSize() - offset;
	}

	public EBitString binary() {
		return bin;
	}

	public static EBitString bs_context_to_binary (EObject obj) {
		EBinMatchState bms;
		if ((bms=obj.testBinMatchState()) != null) {
		    if (bms.offset % 8 == 0) {
				int start_byte = (int)(bms.offset/8);
				EBitString result = EBitString.makeByteOffsetTail(bms.bin, start_byte);
				return result;
			} else {
				// TODO: Convert from start_offset and forth.
				throw new NotImplemented();
			}
		} else {
		    // Note that the source operand may already be a binary...? /eriksoe
			throw new Error("BADARG: be called with EBinMatchState");
		}
	}

	public static void bs_save2 (EObject obj, int slot) {
		EBinMatchState bms;
		if ((bms=obj.testBinMatchState()) != null) {
		    bms.save_offset[slot] = bms.offset;
		} else {
			throw new Error("BADARG: be called with EBinMatchState");
		}
	}

	public static void bs_save2_start (EObject obj) {
		EBinMatchState bms;
		if ((bms=obj.testBinMatchState()) != null) {
		    bms.start_offset = bms.offset;
		} else {
			throw new Error("BADARG: be called with EBinMatchState");
		}
	}
	
	public static void bs_restore2 (EObject obj, int slot) {
		EBinMatchState bms;
		if ((bms=obj.testBinMatchState()) != null) {
			bms.offset = bms.save_offset[slot];
		} else {
			throw new Error("BADARG: be called with EBinMatchState");
		}
	}
	
	public static void bs_restore2_start (EObject obj) {
		EBinMatchState bms;
		if ((bms=obj.testBinMatchState()) != null) {
			bms.offset = bms.start_offset;
		} else {
			throw new Error("BADARG: be called with EBinMatchState");
		}
	}
	
	public static EBinMatchState bs_start_match2(EObject obj, int slots) {
		EBitString bs;
		if ((bs = obj.testBitString()) != null)
			return new EBinMatchState(bs, slots);
		EBinMatchState bms;
		if ((bms=obj.testBinMatchState()) != null) {
			int actual_slots = bms.save_offset.length;
			if (actual_slots < slots) {
				EBinMatchState res = new EBinMatchState(bms.bin, slots);
				res.offset = bms.offset;
				res.save_offset[0] = bms.save_offset[0];
			}
			return bms;
		}
		return null;
	}

	public EBinMatchState(EBitString binary, int slots) {
		this.bin = binary;
		this.offset = 0;
		this.start_offset = 0;
		this.save_offset = new long[Math.min(1, slots)];
	}

	public EBitString bs_get_binary2(EObject spec, int flags) {
		// TODO: Take a unit argument as well.
		long bitLength;
		if (spec == ATOM_ALL) {
			bitLength = bitsLeft();
		} else {
			ESmall len = spec.testSmall();
			if (len != null) {
				bitLength = len.intValue() * 8L;
			} else {
				throw new Error("unknown spec: " + spec);
			}
		}

		if (bitLength > bitsLeft()) {
			return null;
		} else {
			EBitString result = bin.substring(offset, bitLength);
			offset += bitLength;
			return result;
		}
	}

	public EInteger bs_get_integer2(int size, int flags) {

		boolean signed = ((flags & BSF_SIGNED) == BSF_SIGNED);
		boolean little_endian = ((flags & BSF_LITTLE) == BSF_LITTLE);
		boolean native_endian = ((flags & BSF_NATIVE) == BSF_NATIVE);

		if (native_endian) {
			little_endian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
		}
		
		if (size == 0) {
			return ESmall.ZERO;
		}

		if (size < 0 || bin.bitSize() < (offset + size)) {
			// no match
			return null;
		}

		if (size <= 32) {
			int value = bin.intBitsAt(offset, size);
			if (little_endian) {
				int i1 = (value >>> 24) & 0xff;
				int i2 = (value >>> 16) & 0xff;
				int i3 = (value >>> 8) & 0xff;
				int i4 = value & 0xff;
				
				value = i4 << 24;
				value |= i3 << 16;
				value |= i2 << 8;
				value |= i1;
			}
			if (signed) {
				value = EBitString.signExtend(value, size);
			}
			ESmall res = new ESmall(value);
			offset += size;
			return res;
		}

		if (size <= 64) {
			long value = bin.longBitsAt(offset, size);
			if (little_endian) {
				throw new Error("little endian not supported: " + flags);
			}

			if (signed) {
				value = EBitString.signExtend(value, size);
			}
			EInteger res = ERT.box(value);
			offset += size;
			return res;
		}

		byte[] data;
		int extra_in_front = (size%8);
		if (extra_in_front != 0) {
			int bytes_needed = size/8 + 1;
			data = new byte[bytes_needed];
			int out_offset = 0;

			data[0] = (byte) bin.intBitsAt(offset, extra_in_front);
			out_offset = 1;
			offset += extra_in_front;
			
			if (signed) {
				// in this case, sign extend the extra bits to 8 bits
				data[0] = (byte) ( 0xff & EBitString.signExtend(data[0], extra_in_front) );
			}

			for (int i = 0; i < size/8; i++) {
				data[out_offset+i] = (byte) bin.intBitsAt(offset, 8);
				offset += 8;
			}
			
		} else {

			// if signed, the MSB of data[0] is the sign.
			// if unsigned, but a 0-byte in front to make it unsiged
			
			int bytes_needed = size/8 + (signed ? 0 : 1);
			data = new byte[bytes_needed];
			int out_offset = signed ? 0 : 1;

			for (int i = 0; i < size/8; i++) {
				data[out_offset+i] = (byte) bin.intBitsAt(offset, 8);
				offset += 8;
			}
			
		}
		
		
		BigInteger bi = new BigInteger(data);
		return ERT.box(bi);
	}

	public EBitString bs_match_string(EBitString ebs) {
		long size = ebs.bitSize();

		// do we have bits enough in the input
		if (size > bitsLeft()) {
			return null;
		}

		long limit = size;

		for (int pos = 0; pos < limit; pos += 8) {
			int rest = (int) Math.min(8, limit - pos);

			int oc1 = 0xff & bin.intBitsAt(offset+pos, rest);
			int oc2 = 0xff & ebs.intBitsAt(pos, rest);

			if (oc1 != oc2)
				return null;
		}

		offset += size;
		return ebs;

	}

	public EObject bs_skip_bits2(EObject count_o, int bits, int flags) {
		EInteger count;
		if ((count = count_o.testInteger()) == null) {
			// throw badarg?
			return null;
		}

		int bitsWanted = bits * count.intValue();

		if (bitsLeft() < bitsWanted) {
			return null;
		}

		offset += bitsWanted;

		return ERT.TRUE;
	}
	
	public boolean bs_test_unit(int size) {
		return (offset % size == 0);
	}

	/** Tests whether there is exactly expected_left bits left. */
	public boolean bs_test_tail2(int expected_left) {
		return (offset + (long)expected_left == bin.bitSize());
	}

    //==================== UTF-related operations ====================

	public boolean bs_skip_utf8(int flags) {
		long save_offset = offset;
		int character = decodeUTF8();
		if (character<0) assert(offset == save_offset);
		return (character >= 0);
	}

	/** Returns the obtained character, or -1 in case of decoding failure. */
	public int bs_get_utf8(int flags) {
		long save_offset = offset;
		int character = decodeUTF8();
		if (character<0) assert(offset == save_offset);
		return character;
	}

	public boolean bs_skip_utf16(int flags) {
		long save_offset = offset;
		int character = decodeUTF16(flags);
		if (character<0) assert(offset == save_offset);
		return (character >= 0);
	}

	/** Returns the obtained character, or -1 in case of decoding failure. */
	public int bs_get_utf16(int flags) {
		long save_offset = offset;
		int character = decodeUTF16(flags);
		if (character<0) assert(offset == save_offset);
		return character;
	}

	public boolean bs_skip_utf32(int flags) {
		long save_offset = offset;
		int character = decodeUTF32(flags);
		if (character<0) assert(offset == save_offset);
		return (character >= 0);
	}

	/** Returns the obtained character, or -1 in case of decoding failure. */
	public int bs_get_utf32(int flags) {
		long save_offset = offset;
		int character = decodeUTF32(flags);
		if (character<0) assert(offset == save_offset);
		return character;
	}

	/** Decode an UTF-8 character and advance offset.
	 *  If decoding fails, return -1, leaving offset unchanged.
	 */
    static final int[] UTF8_MASK = {0, 0x7F, 0x7FF, 0xFFFF, 0x1FFFFF};
	public int decodeUTF8() {
		if (offset % 8 != 0) return -1; // Misaligned.
		int byte_pos = (int)(offset/8);

		if (bitsLeft() < 8) return -1;
		int acc = bin.octetAt(byte_pos++);

		if (acc < 0x80) {offset=8L * byte_pos; return acc;} // ASCII case.
		if ((acc & 0xC0) == 0x80) return -1; // Sequence starts with a continuation byte.
		if (acc > 0xF8) return -1;           // Out of range.

		int len;
		byte t = (byte)acc;
		for (len=1; (t<<=1) < 0; len++) {
			if (bitsLeft()-8*len < 8) return -1;
			int b = bin.octetAt(byte_pos++);

			if ((b & 0xC0) != 0x80) return -1; // Incorrect continuation byte.
			acc = (acc<<6) + (b & 0x3F);
		}

		acc &= UTF8_MASK[len];
		if (acc <= UTF8_MASK[len-1]) return -1; // Over-long encoding.

		if (!isValidCodePoint(acc)) return -1;

		// Non-ASCII success
		offset = 8L * byte_pos;
		return acc;
    }

	public int decodeUTF16(int flags) {
		if (offset % 8 != 0) return -1; // Misaligned.
		int byte_pos = (int)(offset/8);

		if (bitsLeft() < 16) return -1;
		int acc = bin.uint16_at(byte_pos, flags); byte_pos+=2;

		if ((acc & ~0x3FF) == 0xD800) { // Two-word case
			if (bitsLeft() < 2*16) return -1;
			int w2 = bin.uint16_at(byte_pos, flags); byte_pos+=2;

			if ((w2 & ~0x3FF) != 0xDC00) return -1; // Bad continuation.

			acc = ((acc & 0x3FF)<<10) + (w2 & 0x3FF) + 0x10000;
		} else if ((acc & ~0x3FF) == 0xDC00) {
			return -1; // Bad first word.
		}

		if (!isValidCodePoint(acc)) return -1;

		offset = 8L * byte_pos;
		return acc;
	}

	public int decodeUTF32(int flags) {
		if (offset % 8 != 0) return -1; // Misaligned.
		int byte_pos = (int)(offset/8);

		if (bitsLeft() < 32) return -1;
		int acc = bin.int32_at(byte_pos, flags);

		if (acc < 0 || !isValidCodePoint(acc)) return -1;

		offset += 32;
		return acc;
	}

	public static boolean isValidCodePoint(int c) {
		return ((c & ~0x7FF) != 0xD800 && // First invalid range
				(c & ~0x1) != 0xFFFE   && // Second invalid range
				c < 0x110000);            // Third invalid range
	}
}
