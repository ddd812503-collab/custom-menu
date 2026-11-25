package com.custombg.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Список фонов (встроенные + свои из папки .minecraft/custom_backgrounds)
 * и сохранение выбора в config/custombg.properties.
 */
public final class Backgrounds {
	public static final String MOD_ID = "custombg";
	private static final Logger LOGGER = LoggerFactory.getLogger("custombg");
	private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "bmp", "gif");
	private static final int MAX_WIDTH = 1920;
	private static final int MAX_HEIGHT = 1080;

	public static final String VANILLA_ID = "vanilla";

	/** Один вариант фона. Для своих картинок texture появляется после ensureLoaded(). */
	public static final class Entry {
		private final String id;
		private final Component name;
		private final Path file; // null у встроенных
		private Identifier texture;
		private int width = 1280;
		private int height = 720;
		private boolean failed;

		private Entry(String id, Component name, Path file, Identifier texture) {
			this.id = id;
			this.name = name;
			this.file = file;
			this.texture = texture;
		}

		public String id() {
			return id;
		}

		public Component name() {
			return name;
		}

		public boolean isVanilla() {
			return VANILLA_ID.equals(id);
		}

		public Identifier texture() {
			return texture;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}
	}

	private record Loaded(Identifier texture, int width, int height, long lastModified) {
	}

	private static final List<Entry> BUILTIN = new ArrayList<>();
	private static final List<Entry> entries = new ArrayList<>();
	private static final Map<String, Loaded> loadedCache = new HashMap<>();
	private static String selectedId = VANILLA_ID;
	private static boolean configLoaded = false;

	static {
		BUILTIN.add(new Entry(VANILLA_ID, Component.translatable("custombg.bg.vanilla"), null, null));
		for (String id : List.of("sunset", "night", "aurora", "ocean", "forest", "nether", "end", "desert")) {
			BUILTIN.add(new Entry(id, Component.translatable("custombg.bg." + id), null,
					Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/backgrounds/" + id + ".png")));
		}
		entries.addAll(BUILTIN);
	}

	private Backgrounds() {
	}

	// ---------- папка со своими картинками ----------

	public static Path customFolder() {
		return FabricLoader.getInstance().getGameDir().resolve("custom_backgrounds");
	}

	public static boolean isImageFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		return dot > 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1));
	}

	/** Пересканировать папку со своими фонами. */
	public static void refresh() {
		ensureConfigLoaded();
		entries.clear();
		entries.addAll(BUILTIN);

		Path folder = customFolder();
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			LOGGER.warn("Не удалось создать папку {}", folder, e);
			return;
		}
		try (Stream<Path> files = Files.list(folder)) {
			files.filter(Files::isRegularFile)
					.filter(Backgrounds::isImageFile)
					.sorted()
					.forEach(file -> {
						String fileName = file.getFileName().toString();
						int dot = fileName.lastIndexOf('.');
						entries.add(new Entry("custom:" + fileName, Component.literal(fileName.substring(0, dot)), file, null));
					});
		} catch (IOException e) {
			LOGGER.warn("Не удалось прочитать папку {}", folder, e);
		}

		// если выбранная своя картинка пропала — возвращаемся к стандартному фону
		if (findById(selectedId) == null) {
			selectedId = VANILLA_ID;
		}
	}

	public static List<Entry> entries() {
		ensureConfigLoaded();
		return entries;
	}

	private static Entry findById(String id) {
		for (Entry e : entries) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}

	// ---------- выбор ----------

	public static boolean isSelected(Entry entry) {
		ensureConfigLoaded();
		return entry.id.equals(selectedId);
	}

	public static void select(Entry entry) {
		ensureConfigLoaded();
		if (!ensureLoaded(entry)) {
			return;
		}
		selectedId = entry.id;
		saveConfig();
	}

	public static void selectById(String id) {
		Entry entry = findById(id);
		if (entry != null) {
			select(entry);
		}
	}

	/** Текущий фон с загруженной текстурой, либо null (стандартный фон). */
	public static Entry current() {
		ensureConfigLoaded();
		Entry entry = findById(selectedId);
		if (entry == null || entry.isVanilla() || !ensureLoaded(entry)) {
			return null;
		}
		return entry;
	}

	// ---------- загрузка текстур (только в потоке рендера) ----------

	private static boolean ensureLoaded(Entry entry) {
		if (entry.texture != null) {
			return true;
		}
		if (entry.isVanilla()) {
			return true;
		}
		if (entry.failed || entry.file == null) {
			return false;
		}
		try {
			long modified = Files.getLastModifiedTime(entry.file).toMillis();
			Loaded cached = loadedCache.get(entry.id);
			if (cached != null && cached.lastModified == modified) {
				entry.texture = cached.texture;
				entry.width = cached.width;
				entry.height = cached.height;
				return true;
			}

			BufferedImage source = ImageIO.read(entry.file.toFile());
			if (source == null) {
				throw new IOException("неподдерживаемый формат изображения");
			}
			BufferedImage image = toArgbAndLimit(source);

			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(image, "png", png);
			NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));

			Identifier id = Identifier.fromNamespaceAndPath(MOD_ID,
					"user/" + sanitize(entry.id) + "_" + Long.toHexString(modified));
			DynamicTexture texture = new DynamicTexture(id::toString, nativeImage);
			Minecraft.getInstance().getTextureManager().register(id, texture);

			entry.texture = id;
			entry.width = image.getWidth();
			entry.height = image.getHeight();
			loadedCache.put(entry.id, new Loaded(id, entry.width, entry.height, modified));
			return true;
		} catch (Throwable t) {
			LOGGER.error("Не удалось загрузить фон {}", entry.file, t);
			entry.failed = true;
			return false;
		}
	}

	/** Приводим к ARGB и уменьшаем слишком большие картинки (качественно, чтобы не было «лесенки»). */
	private static BufferedImage toArgbAndLimit(BufferedImage source) {
		int w = source.getWidth();
		int h = source.getHeight();
		double scale = Math.min(1.0, Math.min((double) MAX_WIDTH / w, (double) MAX_HEIGHT / h));
		int nw = Math.max(1, (int) Math.round(w * scale));
		int nh = Math.max(1, (int) Math.round(h * scale));

		BufferedImage result = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, 0, 0, nw, nh, null);
		} finally {
			g.dispose();
		}
		return result;
	}

	private static String sanitize(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
			sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : '_');
		}
		return sb.append('_').append(Integer.toHexString(s.hashCode())).toString();
	}

	// ---------- конфиг ----------

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
	}

	private static void ensureConfigLoaded() {
		if (configLoaded) {
			return;
		}
		configLoaded = true;
		Path path = configPath();
		if (Files.exists(path)) {
			Properties props = new Properties();
			try (InputStream in = Files.newInputStream(path)) {
				props.load(in);
				selectedId = props.getProperty("background", VANILLA_ID);
			} catch (IOException ignored) {
			}
		}
		refresh();
	}

	private static void saveConfig() {
		Properties props = new Properties();
		props.setProperty("background", selectedId);
		try (OutputStream out = Files.newOutputStream(configPath())) {
			props.store(out, "Custom Background mod");
		} catch (IOException ignored) {
		}
	}
}
