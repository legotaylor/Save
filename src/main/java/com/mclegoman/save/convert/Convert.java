/*
    Save
    Contributor(s): dannytaylor
    Github: https://github.com/mclegoman/mclm_save
    Licence: GNU LGPLv3
*/

package com.mclegoman.save.convert;

import com.mclegoman.save.api.exception.ConvertFailException;
import com.mclegoman.save.api.gui.screen.ConfirmScreen;
import com.mclegoman.save.api.gui.screen.InfoScreen;
import com.mclegoman.save.classicexplorer.fields.*;
import com.mclegoman.save.classicexplorer.fields.Class;
import com.mclegoman.save.classicexplorer.io.Reader;
import com.mclegoman.save.config.SaveConfig;
import com.mclegoman.save.data.Data;
import com.mclegoman.save.gui.screen.SaveInfoScreen;
import com.mclegoman.save.level.SaveModLevel;
import com.mclegoman.save.level.SaveModMinecraft;
import com.mclegoman.save.nbt.*;
import com.mclegoman.save.rtu.util.LogType;
import com.mclegoman.save.util.SaveHelper;
import net.minecraft.client.C_5664496;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Random;

public class Convert {
	public static void start(C_5664496 minecraft, Screen parent, final int slot) {
		Data.Resources.minecraft.m_6408915(new ConvertWorldInfoScreen(parent, "Select world file to convert."));
		ConvertDialog convertDialog = new ConvertDialog(minecraft, parent, slot);
		convertDialog.start();
	}

	protected static void process(C_5664496 minecraft, Screen parent, final int slot, final File input) {
		Data.getVersion().sendToLog(LogType.INFO, "Converting '" + input.getName() + "' to Alpha save format!");
		try {
			String worldName = "World" + slot;
			Version version = (input.getName().endsWith(".mine") || input.getName().endsWith(".dat")) ? Version.classic : (input.getName().endsWith(".mclevel") ? Version.indev : null);
			if (version != null) convert(minecraft, version, parent, worldName, input);
			else select(minecraft, parent, worldName, input);
		} catch (Exception error) {
			error(minecraft, parent, error.getLocalizedMessage());
		}
	}

	private static void select(C_5664496 minecraft, Screen parent, final String worldName, final File input) {
		// This function is run when we can't work out what level format we're converting.
		minecraft.m_6408915(new ConfirmScreen(new ConvertWorldInfoScreen(parent, "Converting world...", worldName, input), "Converting world...", "What level format are you converting from?", 0, "Classic", "Indev"));
	}

	private static void convert(C_5664496 minecraft, Version version, Screen parent, final String worldName, final File input) {
		// This function starts the conversion process by asking the user whether they want player data to be converted.
		minecraft.m_6408915(new ConfirmScreen(new ConvertWorldInfoScreen(version, parent, "Converting " + version.getName() + " world...", worldName, input), "Do you want to keep your player data?", "This includes your inventory, and location!", 1));
	}

	protected static void result(C_5664496 minecraft, Version version, Screen parent, final String worldName, final File input, final int id, final boolean value) {
		// 0: Version Type
		if (id == 0) convert(minecraft, version, parent, worldName, input);
			// 1: Convert Player Data
		else if (id == 1) {
			setOverlay("Converting level", "Converting from " + version.getName() + " to alpha format!");
			if (version == Version.classic) convertClassic(minecraft, parent, worldName, value, input);
			else if (version == Version.indev) convertIndev(minecraft, parent, worldName, value, input);
				// This won't be executed unless the Version enum has been modified.
			else error(minecraft, parent, "Invalid version type!");
		}
	}

	protected static void result(C_5664496 minecraft, Screen parent, final String worldName, final int id, final int value, final short width, final short height, final short length, final NbtCompound nbtCompound, final NbtCompound player, final WorldData worldData) {
		// 0: Classic Y Offset
		if (id == 0) convertClassicFinish(minecraft, parent, worldName, width, height, length, worldData.blocks, player, worldData.time, worldData.seed, worldData.spawnX, worldData.spawnY, worldData.spawnZ, value);
		// 1: Indev Y Offset
		if (id == 1) convertIndevFinish(minecraft, parent, worldName, width, height, length, nbtCompound, player, value);
	}

	private static void convertClassic(C_5664496 minecraft, Screen parent, final String worldName, final boolean convertPlayerData, final File input) {
		try {
			setOverlay("Converting level", "Reading data...");
			long seed = new Random().nextLong();
			int spawnX = SaveConfig.instance.conversionSettings.spawnX.value();
			int spawnY = SaveConfig.instance.conversionSettings.spawnY.value();
			int spawnZ = SaveConfig.instance.conversionSettings.spawnZ.value();
			int time = SaveConfig.instance.conversionSettings.time.value();
			byte[] blocks = null;
			ClassField blockMap = null;
			short height = SaveConfig.instance.conversionSettings.height.value().shortValue();
			short length = SaveConfig.instance.conversionSettings.length.value().shortValue();
			short width = SaveConfig.instance.conversionSettings.width.value().shortValue();
			for (Field field : Reader.read(input).getFields()) {
                switch (field.getFieldName()) {
                    case "createTime":
                        seed = (long) field.getField();
                        break;
                    case "xSpawn":
                        spawnX = (int) field.getField();
                        break;
                    case "ySpawn":
                        spawnY = (int) field.getField();
                        break;
                    case "zSpawn":
                        spawnZ = (int) field.getField();
                        break;
                    case "tickCount":
                        time = (int) field.getField();
                        break;
                    case "blocks":
                        blocks = ((BlocksField) field).getBlocks();
                        break;
                    case "blockMap":
                        blockMap = ((ClassField) field);
                        break;
                    case "width":
                        // We get the short value of the stringified value as it could either be a short or an int, depending on the version it was saved in.
                        width = Short.parseShort(String.valueOf(field.getField()));
                        break;
                    case "height":
                        // We get the short value of the stringified value as it could either be a short or an int, depending on the version it was saved in.
                        length = Short.parseShort(String.valueOf(field.getField())); // Was changed from "height" to "length" in Indev.

                        break;
                    case "depth":
                        // We get the short value of the stringified value as it could either be a short or an int, depending on the version it was saved in.
                        height = Short.parseShort(String.valueOf(field.getField())); // Was changed from "depth" to "height" in Indev.

                        break;
                }
			}
			if (blocks == null) error(minecraft, parent, "No blocks found!");
			else {
				if (blocks.length == (width * height * length)) {
					NbtCompound playerData = null;
					if (convertPlayerData) {
						if (blockMap != null) {
							for (Field field : blockMap.getClassField().getFields()) {
								if (field.getFieldName().equals("all")) {
									for (Class entityData : ((ClassField)field).getArrayList()) {
										if (entityData.getName().equals("com.mojang.minecraft.player.Player")) {
											NbtCompound data = new NbtCompound();
											data.putString("id", "LocalPlayer");
											entityData.getFields().forEach(player -> {
												if (player.getFieldName().equals("inventory")) {
													NbtList inventory = new NbtList();
													ArrayList<Field> inventory1 = ((ClassField)player).getClassField().getFields();
													inventory1.forEach(invField -> {
														if (invField.getFieldName().equals("count")) {
															for (Field field2 : ((ArrayField)invField).getArray()) {
																NbtCompound itemData = new NbtCompound();
																byte count = ((Integer)field2.getField()).byteValue();
																itemData.putByte("Count", count);
																if (count != 0) inventory.add(itemData);
															}
														} else if (invField.getFieldName().equals("slots")) {
															int index = 0;
															int slot = 0;
															for (Field field2 : ((ArrayField)invField).getArray()) {
																short id = ((Integer)field2.getField()).shortValue();
																if (id >= 0) {
																	NbtCompound itemData = (NbtCompound) inventory.get(index);
																	itemData.putShort("id", id);
																	itemData.putByte("Slot", ((Integer)slot).byteValue());
																	index++;
																}
																slot++;
															}
														}
													});
													data.put("Inventory", inventory);
												}
												if (player.getFieldName().equals("score")) {
													data.putInt("Score", (int) player.getField());
												}
											});
											NbtList motion = SaveHelper.toNbtList(0.0D, 0.0D, 0.0D);
											NbtList pos = new NbtList();
											NbtList rotation = SaveHelper.toNbtList(0.0F, 0.0F);
											entityData.getSuperClass().getSuperClass().getFields().forEach(entity -> {
												if (entity.getFieldName().equals("x") || entity.getFieldName().equals("y") || entity.getFieldName().equals("z")) {
													pos.add(new NbtDouble((float) entity.getField()));
												}
												if (entity.getFieldName().equals("fallDistance")) {
													data.putFloat("FallDistance", (float) entity.getField());
												}
											});
											data.put("Motion", motion);
											data.put("Pos", pos);
											data.put("Rotation", rotation);
											data.putShort("Air", (short) 300);
											data.putShort("AttackTime", (short) 0);
											data.putShort("DeathTime", (short) 0);
											data.putShort("HurtTime", (short) 0);
											data.putShort("Health", (short) 20);
											data.putShort("Fire", (short) -20);
											playerData = data;
											break;
										}
									}
								}
							}
						}
					}
					int maxYOffset = 128 - height;
					if (maxYOffset > 0) minecraft.m_6408915(new SliderConfirmScreen(new ConvertWorldInfoScreen(parent, "Setting y offset...", worldName, input, width, length, height, null, playerData, new WorldData(blocks, time, seed, (short) spawnX, (short) spawnY, (short) spawnZ)), "Do you want to offset your world vertically?", "Select how many blocks upwards you want to shift your world", 0, "Y Offset", maxYOffset, "Confirm"));
					else convertClassicFinish(minecraft, parent, worldName, width, height, length, blocks, playerData, time, seed, (short) spawnX, (short) spawnY, (short) spawnZ, 0);
				} else throw new ConvertFailException("Invalid block amount!");
			}
		} catch (Exception error) {
			error(minecraft, parent, error.getLocalizedMessage());
		}
	}

	private static void convertIndev(C_5664496 minecraft, Screen parent, final String worldName, final boolean convertPlayerData, final File input) {
		try {
			NbtCompound nbtCompound = SaveModLevel.load(Files.newInputStream(input.toPath()));
			NbtCompound map = nbtCompound.getCompound("Map");
			NbtCompound player = null;
			if (convertPlayerData) {
				for (int i = 0; i < nbtCompound.getList("Entities").size(); i++) {
					NbtCompound entity = (NbtCompound) nbtCompound.getList("Entities").get(i);
					if (entity.containsKey("id") && entity.getString("id").equals("LocalPlayer")) {
						player = entity;
						break;
					}
				}
				// The only difference between indev player data and infdev player data is
				// that infdev uses double instead of float for motion and pos.
				if (player != null) {
					NbtList motion = player.getList("Motion");
					NbtList newMotion = new NbtList();
					for (int i = 0; i < motion.size(); i++) newMotion.add(new NbtDouble(((NbtFloat)motion.get(i)).value));
					player.put("Motion", newMotion);
					NbtList pos = player.getList("Pos");
					NbtList newPos = new NbtList();
					for (int i = 0; i < pos.size(); i++) newPos.add(new NbtDouble(((NbtFloat)pos.get(i)).value));
					player.put("Pos", newPos);
				}
			}
			short width = map.getShort("Width");
			short length = map.getShort("Length");
			short height = map.getShort("Height");
			int maxYOffset = 128 - height;
			if (maxYOffset > 0) minecraft.m_6408915(new SliderConfirmScreen(new ConvertWorldInfoScreen(parent, "Setting y offset...", worldName, input, width, length, height, nbtCompound, player, null), "Do you want to offset your world vertically?", "Select how many blocks upwards you want to shift your world", 1, "Y Offset", maxYOffset, "Confirm"));
			else convertIndevFinish(minecraft, new ConvertWorldInfoScreen(parent, "Setting y offset..."), worldName, width, height, length, nbtCompound, player, 0);
		} catch (Exception error) {
			error(minecraft, parent, error.getLocalizedMessage());
		}
	}

	private static void convertClassicFinish(C_5664496 minecraft, Screen parent, String worldName, short width, short height, short length, byte[] blocks, NbtCompound player, long time, long seed, short spawnX, short spawnY, short spawnZ, int yOffset) {
		try {
			File file = new File(SaveHelper.getSavesDir(), worldName);
			convertBlocks(file, width, height, length, blocks, null, time, yOffset);
			createLevel(minecraft, parent, file, seed, spawnX, spawnY + yOffset, spawnZ, time, calculateSizeOnDisk(file, width, length), player);
			done(minecraft, parent, worldName);
		} catch (Exception error) {
			error(minecraft, parent, error.getLocalizedMessage());
		}
	}

	private static void convertIndevFinish(C_5664496 minecraft, Screen parent, String worldName, short width, short height, short length, NbtCompound nbtCompound, NbtCompound player, int yOffset) {
		try {
			NbtCompound map = nbtCompound.getCompound("Map");
			long seed = nbtCompound.getCompound("About").getLong("CreatedOn");
			short spawnX = ((NbtShort) map.getList("Spawn").get(0)).value;
			short spawnY = ((NbtShort) map.getList("Spawn").get(1)).value;
			short spawnZ = ((NbtShort) map.getList("Spawn").get(2)).value;
			long time = nbtCompound.getCompound("Map").getShort("TimeOfDay");
			File file = new File(SaveHelper.getSavesDir(), worldName);
			convertBlocks(file, width, height, length, map.getByteArray("Blocks"), map.containsKey("Data") ? map.getByteArray("Data") : null, time, yOffset);
			// If we were to convert entities, they would be converted here.
			// Note: we don't convert currently, as the next version of infdev, doesn't save/load entities.
			convertTileEntities(file, nbtCompound.getList("TileEntities"), yOffset);
			if (player != null) {
				NbtList pos = player.getList("Pos");
				pos.replace(1, new NbtDouble((((NbtDouble)pos.get(1)).value) + yOffset));
				player.put("Pos", pos);
			}
			createLevel(minecraft, parent, file, seed, spawnX, spawnY + yOffset, spawnZ, time, calculateSizeOnDisk(file, width, length), player);
			done(minecraft, parent, worldName);
		} catch (Exception error) {
			error(minecraft, parent, error.getLocalizedMessage());
		}
	}

	private static void convertTileEntities(File dir, NbtList tileEntities, int yOffset) throws IOException {
		for (int i = 0; i < tileEntities.size(); i++) {
			setOverlay("Converting level", "Converting tile entities... (" + i + "/" + tileEntities.size() + ")");
			NbtElement tileEntity = tileEntities.get(i);
			if (tileEntity.getType() == 10) {
				NbtCompound tile = (NbtCompound)tileEntity;
				if (tile.containsKey("Pos")) {
					int pos = tile.getInt("Pos");
					// https://minecraft.wiki/w/Java_Edition_Indev_level_format
					int x = pos % 1024;
					int y = ((pos >> 10) % 1024) + yOffset;
					int z = (pos >> 20) % 1024;
					tile.remove("Pos");
					tile.putInt("x", x);
					tile.putInt("y", y);
					tile.putInt("z", z);
					int chunkX = x / 16;
					int chunkZ = z / 16;
					if (x >= chunkX * 16 && x < (chunkX + 1) * 16 && z >= chunkZ * 16 && z < (chunkZ + 1) * 16) {
						if (tile.getString("id").equals("Chest")) {
							File file = SaveHelper.getChunkFile(dir, chunkX, chunkZ);
							NbtCompound chunk = SaveModLevel.load(Files.newInputStream(file.toPath())).getCompound("Level");
							NbtList chunkTileEntities = chunk.getList("TileEntities");
							chunkTileEntities.add(tile);
							NbtCompound level = new NbtCompound();
							level.put("Level", chunk);
							SaveModLevel.save(level, Files.newOutputStream(file.toPath()));
						}
					}
				}
			}
		}
	}

	private static long calculateSizeOnDisk(final File dir, final short width, final short length) {
		long sizeOnDisk = 0L;
		int total = ((width / 16) * (length / 16));
		for (int chunk = 0; chunk < total; chunk++) sizeOnDisk += SaveHelper.getChunkFile(dir, chunk % (width / 16), chunk / (width / 16)).length();
		return sizeOnDisk;
	}

	private static void convertBlocks(final File dir, final short width, final short height, final short length, final byte[] blocks, final byte[] blocksData, final long ticks, final int yOffset) throws ConvertFailException, IOException {
		// inf-20100227 changed the world height from 256, to 127.
		// https://minecraft.wiki/w/Java_Edition_Infdev_20100227-1414
		if (width % 16 != 0) throw new ConvertFailException("Width was " + width + ", expecting value divisible by 16!");
		if (height <= 0 || height > 127) throw new ConvertFailException("Height was " + height + ", expecting value between 1 and 127!");
		if (length % 16 != 0) throw new ConvertFailException("Length was " + length + ", expecting value divisible by 16!");
		if (blocksData == null) Data.getVersion().sendToLog(LogType.WARN, "No block data present: Block light and metadata will be set to default, you may encounter lag when these update for the first time.");
		if (blocks.length == width * height * length) {
			int total = ((width / 16) * (length / 16));
			for (int chunk = 0; chunk < total; chunk++) {
				setOverlay("Converting level", "Writing chunk data... (" + chunk + "/" + total + ")");
				int x = chunk % (width / 16);
				int z = chunk / (width / 16);
				File chunkFile = SaveHelper.getChunkFile(dir, x, z);
				NbtCompound chunkData = new NbtCompound();
				NbtCompound level = new NbtCompound();
				level.putInt("xPos", x);
				level.putInt("zPos", z);
				level.putLong("LastUpdate", ticks);
				byte[] chunkBlocks = getBlocksForChunk(x, z, width, height, length, blocks, yOffset);
				level.putByteArray("Blocks", chunkBlocks);
				level.putByteArray("Data", blocksData != null ? getBlockDataForChunk(x, z, width, height, length, blocksData, yOffset, false) : new byte[16 * 16 * 64]);
				level.putByteArray("SkyLight", new byte[16 * 16 * 128]);
				level.putByteArray("BlockLight", blocksData != null ? getBlockDataForChunk(x, z, width, height, length, blocksData, yOffset, true) : new byte[16 * 16 * 64]);
				level.putByteArray("HeightMap", calcHeightMap(chunkBlocks));
				level.put("TileEntities", new NbtList());
				chunkData.put("Level", level);
				SaveModLevel.save(chunkData, Files.newOutputStream(chunkFile.toPath()));
			}
		} else throw new ConvertFailException("Invalid block amount!");
	}

	private static byte[] calcHeightMap(final byte[] chunkBlocks) {
		byte[] heightMap = new byte[16 * 16];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				byte h = 0;
				for (int y = 127; y >= 0; y--) {
					byte block = chunkBlocks[(y * 16 + z) * 16 + x];
					if (block != 0) {
						h = (byte) (y + 1);
						break;
					}
				}
				heightMap[x * 16 + z] = h;
			}
		}
		return heightMap;
	}

	private static byte[] getBlocksForChunk(final int x, final int z, final int width, final short height, final int length, final byte[] blocks, final int yOffset) {
		byte[] chunk = new byte[16 * 16 * 128];
		int index = 0;
		for (int xChunk = 0; xChunk < 16; xChunk++) {
			for (int zChunk = 0; zChunk < 16; zChunk++) {
				for (int y = 0; y < yOffset; y++) {
					chunk[index] = SaveConfig.instance.conversionSettings.offsetBlockId.value().byteValue();
					index += 1;
				}
				for (int y = 0; y < height; y++) {
					byte block = blocks[(((y * length + (z * 16 + zChunk)) * width) + (x * 16 + xChunk))];
					if (SaveConfig.instance.conversionSettings.replaceBedrock.value() && block == (byte) 7) block = SaveConfig.instance.conversionSettings.offsetBlockId.value().byteValue();
					chunk[index] = block;
					index++;
				}
				index += (128 - height - yOffset);
			}
		}
		return chunk;
	}

	private static byte[] getBlockDataForChunk(final int x, final int z, final int width, final short height, final int length, final byte[] blockData, final int yOffset, final boolean isLight) {
		byte[] output = new byte[16 * 16 * 64];
		int index = 0;
		for (int xIndex = x * 16; xIndex < x * 16 + 16; xIndex++) {
			for (int zIndex = z * 16; zIndex < z * 16 + 16; zIndex++) {
				int y = 0;
				y += yOffset;
				while (y < height) {
					byte a = blockData[(y * length + zIndex) * width + xIndex];
					byte b = 0;
					if (y + 1 < height) b = blockData[((y + 1) * length + zIndex) * width + xIndex];
                    output[index] = (byte) (((a & 0x0F) & 0xF) | ((b & 0x0F) << 4));
                    index++;
					y += 2;
				}
				while (index % 64 != 0) index++;
			}
		}
		return output;
	}

	private static void createLevel(C_5664496 minecraft, Screen parent, final File dir, final long seed, final int spawnX, final int spawnY, final int spawnZ, final long time, final long sizeOnDisk, final @Nullable NbtCompound player) {
		try {
			setOverlay("Converting level", "Writing level data...");
			dir.mkdirs();
			File level = new File(dir, "level.dat");
			NbtCompound data = new NbtCompound();
			data.putLong("RandomSeed", seed);
			data.putInt("SpawnX", spawnX);
			data.putInt("SpawnY", spawnY);
			data.putInt("SpawnZ", spawnZ);
			data.putLong("Time", time);
			data.putLong("SizeOnDisk", sizeOnDisk);
			data.putLong("LastPlayed", System.currentTimeMillis());
			if (player != null) data.putCompound("Player", player);
			NbtCompound output = new NbtCompound();
			output.put("Data", data);
			SaveModLevel.save(output, Files.newOutputStream(level.toPath()));
		} catch (Exception error) {
			error(minecraft, parent, error.getLocalizedMessage());
		}
	}

	private static void done(C_5664496 minecraft, Screen parent, final String worldName) {
		System.gc();
		if (SaveConfig.instance.shouldLoadAfterConvert.value()) {
			((SaveModMinecraft)minecraft).save$set(worldName);
			minecraft.m_6408915(null);
		} else minecraft.m_6408915(new SaveInfoScreen(parent, "Convert World", "Successfully converted world to '" + worldName + "'!", InfoScreen.Type.DIRT, true));
	}

	private static void error(C_5664496 minecraft, Screen parent, String error) {
		minecraft.m_6408915(new SaveInfoScreen(parent, "Error!", ((error == null || error.isEmpty()) ? "Failed to convert world!" : error), InfoScreen.Type.ERROR, true));
	}

	public enum Version {
		classic("classic"),
		indev("indev");
		private String name;
		Version(String name) {
			this.name = name;
		}
		public String getName() {
			return this.name;
		}
	}

	private static void setOverlay(String title, String message) {
		setOverlay(title, message, -1);
	}

	private static void setOverlay(String title, String message, int value) {
		SaveHelper.infoOverlay.setTitle(title);
		SaveHelper.infoOverlay.setDescription(message);
		SaveHelper.infoOverlay.setLoading(value);
	}

	public static class WorldData {
		public byte[] blocks;
		public long time;
		public long seed;
		public short spawnX;
		public short spawnY;
		public short spawnZ;
		public WorldData(byte[] blocks, long time, long seed, short spawnX, short spawnY, short spawnZ) {
			this.blocks = blocks;
			this.time = time;
			this.seed = seed;
			this.spawnX = spawnX;
			this.spawnY = spawnY;
			this.spawnZ = spawnZ;
		}
	}
}