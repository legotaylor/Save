/*
    Save
    Contributor(s): dannytaylor
    Github: https://github.com/mclegoman/mclm_save
    Licence: GNU LGPLv3
*/

package com.mclegoman.save.level;

import com.mclegoman.save.nbt.NbtCompound;
import com.mclegoman.save.nbt.NbtElement;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SaveModLevel {
	public static NbtCompound load(InputStream inputStream) throws IOException {
		try (GZIPInputStream gzipInput = new GZIPInputStream(new BufferedInputStream(inputStream)); DataInputStream dataInput = new DataInputStream(gzipInput)) {
			NbtElement root = NbtElement.deserialize(dataInput);
			if (!(root instanceof NbtCompound)) throw new IOException("Root tag must be a named compound tag");
			return (NbtCompound) root;
		}
	}
	public static void save(NbtCompound nbtCompound, OutputStream outputStream) throws IOException {
		try (BufferedOutputStream output = new BufferedOutputStream(outputStream); GZIPOutputStream gzipOutput = new GZIPOutputStream(output); DataOutputStream dataOutput = new DataOutputStream(gzipOutput)) {
			NbtElement.serialize(nbtCompound, dataOutput);
		}
	}
}
