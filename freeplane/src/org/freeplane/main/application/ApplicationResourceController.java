/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is created by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.main.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.InvalidPropertiesFormatException;
import java.util.Locale;
import java.util.Properties;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.controller.FreeplaneVersion;
import org.freeplane.core.filter.FilterController;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;

/**
 * @author Dimitry Polivaev
 */
class ApplicationResourceController extends ResourceController {
	public static final String DEFAULT_ORG_FREEPLANE_GLOBALRESOURCEDIR = "resources";
	public static final String ORG_FREEPLANE_GLOBALRESOURCEDIR = "org.freeplane.globalresourcedir";
	final private File autoPropertiesFile;
	final private Properties defProps;
	private LastOpenedList lastOpened;
	final private Properties props;
	private final String resourceBaseDir;
	private ClassLoader urlResourceLoader;

	/**
	 * @param controller
	 */
	public ApplicationResourceController() {
		super();
		urlResourceLoader = null;
		resourceBaseDir = System.getProperty(ApplicationResourceController.ORG_FREEPLANE_GLOBALRESOURCEDIR,
		    ApplicationResourceController.DEFAULT_ORG_FREEPLANE_GLOBALRESOURCEDIR);
		if (resourceBaseDir != null) {
			try {
				final File resourceDir = new File(resourceBaseDir);
				if (resourceDir.exists()) {
					final URL globalResourceUrl = Compat.fileToUrl(resourceDir);
					urlResourceLoader = new URLClassLoader(new URL[] { globalResourceUrl }, null);
				}
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
		}
		defProps = readDefaultPreferences();
		props = readUsersPreferences(defProps);
		createUserDirectory(defProps);
		setDefaultLocale(props);
		autoPropertiesFile = getUserPreferencesFile(defProps);
		addPropertyChangeListener(new IFreeplanePropertyListener() {
			public void propertyChanged(final String propertyName, final String newValue, final String oldValue) {
				if (propertyName.equals(ResourceBundles.RESOURCE_LANGUAGE)) {
					clearLanguageResources();
				}
			}
		});
	}

	private void createUserDirectory(final Properties pDefaultProperties) {
		final File userPropertiesFolder = new File(getFreeplaneUserDirectory(pDefaultProperties));
		try {
			if (!userPropertiesFolder.exists()) {
				userPropertiesFolder.mkdir();
			}
		}
		catch (final Exception e) {
			e.printStackTrace();
			System.err.println("Cannot create folder for user properties and logging: '"
			        + userPropertiesFolder.getAbsolutePath() + "'");
		}
	}

	@Override
	public String getDefaultProperty(final String key) {
		return defProps.getProperty(key);
	}

	@Override
	public String getFreeplaneUserDirectory() {
		return System.getProperty("user.home") + File.separator + getProperty("properties_folder");
	}

	private String getFreeplaneUserDirectory(final Properties defaultPreferences) {
		return System.getProperty("user.home") + File.separator + defaultPreferences.getProperty("properties_folder");
	}

	public LastOpenedList getLastOpenedList() {
		return lastOpened;
	}

	@Override
	public Properties getProperties() {
		return props;
	}

	@Override
	public String getProperty(final String key) {
		return props.getProperty(key);
	}

	@Override
	public URL getResource(final String name) {
		if (urlResourceLoader == null) {
			return super.getResource(name);
		}
		final String relName;
		if (name.startsWith("/")) {
			relName = name.substring(1);
		}
		else {
			relName = name;
		}
		URL resource = urlResourceLoader.getResource(relName);
		if (resource != null) {
			return resource;
		}
		resource = super.getResource(name);
		if (resource != null) {
			return resource;
		}
		if("/lib/freeplaneviewer.jar".equals(name)){
			final String rootDir = new File(getResourceBaseDir()).getAbsoluteFile().getParent();
			try {
	            final File try1 = new File(rootDir + "/plugins/org.freeplane.core/lib/freeplaneviewer.jar");
	            if(try1.exists()){
	            	return try1.toURL();
	            }
	            final File try2 = new File(rootDir + "/lib/freeplaneviewer.jar");
	            if(try2.exists()){
	            	return try2.toURL();
	            }
            }
            catch (MalformedURLException e) {
	            e.printStackTrace();
            }
		}
		return null;
	}

	@Override
	public String getResourceBaseDir() {
		return resourceBaseDir;
	}

	private File getUserPreferencesFile(final Properties defaultPreferences) {
		if (defaultPreferences == null) {
			System.err.println("Panic! Error while loading default properties.");
			System.exit(1);
		}
		final String freeplaneDirectory = getFreeplaneUserDirectory(defaultPreferences);
		final File userPropertiesFolder = new File(freeplaneDirectory);
		final File autoPropertiesFile = new File(userPropertiesFolder, defaultPreferences.getProperty("autoproperties"));
		return autoPropertiesFile;
	}

	@Override
	public void init(final Controller controller) {
		lastOpened = new LastOpenedList(controller);
		super.init(controller);
	}

	@Override
	public void loadProperties(final InputStream inStream) throws IOException {
		defProps.load(inStream);
	}

	@Override
	public void loadPropertiesFromXML(final InputStream in) throws IOException, InvalidPropertiesFormatException {
		defProps.loadFromXML(in);
	}

	private Properties readDefaultPreferences() {
		final String propsLoc = ResourceController.FREEPLANE_PROPERTIES;
		final Properties props = new Properties();
		readDefaultPreferences(props, propsLoc);
		return props;
	}

	private void readDefaultPreferences(final Properties props, final String propsLoc) {
	    final URL defaultPropsURL = getResource(propsLoc);
		try {
			InputStream in = null;
			in = defaultPropsURL.openStream();
			props.load(in);
			in.close();
		}
		catch (final Exception ex) {
			ex.printStackTrace();
			System.err.println("Panic! Error while loading default properties.");
		}
		final String propsLocs = props.getProperty("load_next_properties", "");
		props.setProperty("load_next_properties", "");
		if(propsLocs.equals("")){
			return;
		}
		final String[] locArray = propsLocs.split(";");
		for(String loc:locArray){
			readDefaultPreferences(props, loc);
		}
    }

	private Properties readUsersPreferences(final Properties defaultPreferences) {
		Properties auto = null;
		auto = new Properties(defaultPreferences);
		try {
			InputStream in = null;
			final File autoPropertiesFile = getUserPreferencesFile(defaultPreferences);
			in = new FileInputStream(autoPropertiesFile);
			auto.load(in);
			in.close();
		}
		catch (final Exception ex) {
			System.err.println("User properties not found, new file created");
		}
		return auto;
	}

	@Override
	public void saveProperties(final Controller controller) {
		lastOpened.saveProperties();
		try {
			final OutputStream out = new FileOutputStream(autoPropertiesFile);
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out, "8859_1");
			outputStreamWriter.write("#Freeplane ");
			outputStreamWriter.write(FreeplaneVersion.getVersion().toString());
			outputStreamWriter.write('\n');
			outputStreamWriter.flush();
			props.store(out, null);
			out.close();
		}
		catch (final Exception ex) {
		}
		FilterController.getController(controller).saveConditions();
	}

	/**
	 * @param pProperties
	 */
	private void setDefaultLocale(final Properties pProperties) {
		final String lang = pProperties.getProperty(ResourceBundles.RESOURCE_LANGUAGE);
		if (lang == null) {
			return;
		}
		Locale localeDef = null;
		switch (lang.length()) {
			case 2:
				localeDef = new Locale(lang);
				break;
			case 5:
				localeDef = new Locale(lang.substring(0, 1), lang.substring(3, 4));
				break;
			default:
				return;
		}
		Locale.setDefault(localeDef);
	}

	@Override
	public void setDefaultProperty(final String key, final String value) {
		defProps.setProperty(key, value);
	}

	@Override
	public void setProperty(final String key, final String value) {
		final String oldValue = getProperty(key);
		if (oldValue == value) {
			return;
		}
		if (oldValue != null && oldValue.equals(value)) {
			return;
		}
		props.setProperty(key, value);
		firePropertyChanged(key, value, oldValue);
	}
}
