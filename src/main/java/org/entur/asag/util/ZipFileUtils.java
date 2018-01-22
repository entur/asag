/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package org.entur.asag.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ZipFileUtils {
    private static Logger logger = LoggerFactory.getLogger(ZipFileUtils.class);

    public Set<String> listFilesInZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.stream().filter(ze -> !ze.isDirectory()).map(ze -> ze.getName()).collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    public Set<String> listFilesInZip(byte[] data) {
        return listFilesInZip(new ByteArrayInputStream(data));
    }

    public Set<String> listFilesInZip(InputStream inputStream) {
        Set<String> fileNames = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                fileNames.add(zipEntry.getName());
                zipEntry = zipInputStream.getNextEntry();
            }
            return fileNames;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }





    public static void unzipFile(InputStream inputStream, String targetFolder) {
        try {
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                logger.info("unzipping file: {}", fileName);

                File newFile = new File(targetFolder + "/" + fileName);
                if (fileName.endsWith("/")) {
                    newFile.mkdirs();
                    continue;
                }

                File parent = newFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }


                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException ioE) {
            throw new RuntimeException("Unzipping archive failed: " + ioE.getMessage(), ioE);
        }
    }


    private static File getFile(byte[] data) throws IOException {
        File inputFile = File.createTempFile("marduk-input", ".zip");

        FileOutputStream fos = new FileOutputStream(inputFile);
        fos.write(data);
        fos.close();
        return inputFile;
    }

    private static ZipFile getZipFileIfSingleFolder(File inputFile) throws IOException {

        if (inputFile == null || inputFile.length() == 0) {
            return null;
        }

        ZipFile zipFile = new ZipFile(inputFile);
        boolean allFilesInSingleDirectory = false;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        String directoryName = "";
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            if (zipEntry.isDirectory()) {
                allFilesInSingleDirectory = true;
                directoryName = zipEntry.getName();
            } else {
                if (!zipEntry.getName().startsWith(directoryName)) {
                    allFilesInSingleDirectory = false;
                    break;
                }
            }
        }

        if (allFilesInSingleDirectory) {
            return zipFile;
        }
        return null;
    }


}