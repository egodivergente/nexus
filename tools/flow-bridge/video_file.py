"""Validate an ISO BMFF video container without running a media build."""
import struct

def boxes(data, start=0, end=None):
    end = len(data) if end is None else end
    while start < end:
        if end - start < 8:
            raise ValueError("Truncated MP4 box")
        size, kind = struct.unpack_from(">I4s", data, start)
        header = 8
        if size == 1:
            if end - start < 16:
                raise ValueError("Truncated extended MP4 box")
            size = struct.unpack_from(">Q", data, start + 8)[0]
            header = 16
        if size == 0:
            size = end - start
        if size < header or start + size > end:
            raise ValueError("Invalid MP4 box length")
        yield kind, start + header, start + size
        start += size

def inspect(data):
    top = list(boxes(data))
    kinds = {kind for kind, _, _ in top}
    if not {b"ftyp", b"moov", b"mdat"}.issubset(kinds):
        raise ValueError("MP4 needs ftyp, moov and mdat")
    duration = None
    dimensions = None
    for kind, start, end in top:
        if kind != b"moov":
            continue
        for inner, a, b in boxes(data, start, end):
            if inner == b"mvhd":
                version = data[a]
                offset = a + (20 if version == 1 else 12)
                timescale = struct.unpack_from(">I", data, offset)[0]
                ticks = struct.unpack_from(">Q" if version == 1 else ">I", data, offset + 4)[0]
                duration = ticks / timescale if timescale else None
            if inner == b"trak":
                for track, c, d in boxes(data, a, b):
                    if track == b"tkhd" and d - c >= 8:
                        w, h = struct.unpack_from(">II", data, d - 8)
                        if w and h:
                            dimensions = [w >> 16, h >> 16]
    if not duration or duration <= 0 or not dimensions or min(dimensions) <= 0:
        raise ValueError("MP4 lacks a positive duration and video dimensions")
    return {"format": "MP4", "duration_seconds": duration, "dimensions": dimensions,
            "container_verified": True}
