/*
* Copyright 2013 JBoss, by Red Hat, Inc
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.reflections.vfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;
import org.jboss.vfs.VirtualFile;

/**
 * An {@link Vfs.UrlType} for deployment archives served through the JBoss virtual filesytem. <br/>
 * It simply delegates to {@link SystemDir} and
 * {@link ZipDir} respectively
 *
 * @author Heiko Braun <hbraun@redhat.com>
 * @author Mike Brock <cbrock@redhat.com>
 */
public class JBossVFsTypeHandler implements Vfs.UrlType {

    protected static final Logger log = LoggerFactory.getLogger(JBossVFsTypeHandler.class);

    final static boolean jbossAS;

    final static String VFS = "vfs";
    final static String VFSZIP = "vfszip";
    final static String VFSFILE = "vfsfile";

    static {
        boolean jbossFound;
        try {
            Class.forName("org.jboss.vfs.VirtualFile");
            jbossFound = true;
        } catch (ClassNotFoundException e) {
            jbossFound = false;
        }

        jbossAS = jbossFound;
    }

    public boolean matches(final URL url) {
        return url.getProtocol().equals(VFS)
                || url.getProtocol().equals(VFSZIP)
                || url.getProtocol().equals(VFSFILE);
    }

    public Vfs.Dir createDir(final URL url) {
        // Create non VFS Url
        final File deployment = identifyDeployment(url);
        if (null == deployment) {
            throw new RuntimeException("Unable identify deployment file for: " + url);
        }

        final File file = deployment.getAbsoluteFile();

        try {
            if (jbossAS) {
                try {
                    if (url.getContent() instanceof VirtualFile) {
                        return new JBossVfsDir(url);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("error reading from VFS", e);
                }
            }

            final URL targetURL = file.toURI().toURL();

            // delegate unpacked archives to SystemDir handler
            if (deployment.isDirectory()) {
//                return new SystemDir(targetURL);
                return new SystemDir(file);
            }

            // if it's a file delegate to ZipDir handler
            return new ZipDir(new JarFile(file));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid URL", e);
        } catch (IOException ex) {
            throw new RuntimeException("Invalid URL", ex);
        }
    }

    public static File identifyDeployment(final URL url) {
        String actualFilePath = url.getPath();
        if (actualFilePath.startsWith("file:")) {
            actualFilePath = actualFilePath.substring(5);
        }

        final int nestedSeperator = actualFilePath.indexOf('!');
        if (nestedSeperator != -1) {
            actualFilePath = actualFilePath.substring(0, nestedSeperator);
        }

        log.debug("scanning inside: " + actualFilePath);

        return findActualDeploymentFile(new File(actualFilePath));
    }

    static File findActualDeploymentFile(File start) {
        int pivotPoint;
        String rootPath = start.getPath();

        do {
            start = new File(rootPath);
            rootPath = rootPath.substring(0, (pivotPoint = rootPath.lastIndexOf(File.separator)) < 0 ? 0 : pivotPoint);
        } while (!start.exists() && pivotPoint > 0);

        return start;
    }
}
