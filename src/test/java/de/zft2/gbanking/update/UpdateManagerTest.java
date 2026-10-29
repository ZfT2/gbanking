package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

class UpdateManagerTest {

	private static final int ANNOUNCED_SIZE = 16;

	@TempDir
	Path installDirectory;

	@Test
	void downloadAndPrepare_shouldRejectOversizedDownloadAndRemoveWorkDirectory() throws Exception {
		Files.createDirectories(installDirectory.resolve("bin"));
		Files.createDirectories(installDirectory.resolve("lib"));
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] body = new byte[ANNOUNCED_SIZE * 2];
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream outputStream = exchange.getResponseBody()) {
				outputStream.write(body);
			}
		});
		server.start();
		try {
			URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
			ReleaseAsset asset = new ReleaseAsset("gbanking.zip", baseUri.resolve("gbanking.zip"), ANNOUNCED_SIZE);
			UpdateRelease release = new UpdateRelease("99.0", "v99.0", baseUri, "", asset, asset, asset);
			UpdateManager updateManager = new UpdateManager(HttpClient.newHttpClient(), OperatingSystem.current(), installDirectory,
					null, null, null, null);

			assertThrows(UpdateException.class, () -> updateManager.downloadAndPrepare(release, null));
		} finally {
			server.stop(0);
		}
		try (Stream<Path> updateDirectories = Files.list(installDirectory.resolve(".updates"))) {
			assertEquals(0, updateDirectories.count());
		}
	}
}
