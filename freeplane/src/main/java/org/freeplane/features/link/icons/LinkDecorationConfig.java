package org.freeplane.features.link.icons;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

/**
 * Loads and represents the contents of
 * <code>FREEPLANE_CONF/linkDecoration.ini</code>
 * 
 * @author Stuart Robertson <stuartro@gmail.com>
 */
class LinkDecorationConfig {
	private static final String LINK_DECORATION_INI = "linkDecoration.ini";

	private URL iniFile = ResourceController.getResourceController().getResource(LINK_DECORATION_INI);

	private List<LinkDecorationRule> rules;

	private long iniFileLastModified = -1;

	public LinkDecorationConfig() {

	}

	public List<LinkDecorationRule> getRules() {
		if (iniFile != null && (rules == null || rulesFileHasChangedSinceLastLoad())) {
			loadRules();
		}
		return rules;
	}

	private boolean rulesFileHasChangedSinceLastLoad() {
		if (iniFileLastModified == -1) {
			return true;
		}

		return iniFileLastModified == iniFileLastModified();
	}

	private void loadRules() {
        rules = new ArrayList<LinkDecorationRule>();
		try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(iniFile.openStream()))){
			while (inputStream.ready()) {
			    String line = inputStream.readLine().trim();
				if (isBlank(line) || isComment(line)) {
					continue;
				}
                int descriptionStart = line.lastIndexOf("#");
                int iconNameEnd = descriptionStart == -1 ? line.length() :  descriptionStart;
                int specificationEnd = line.lastIndexOf("|", iconNameEnd);
                if(specificationEnd > 0 && iconNameEnd > specificationEnd) {
                    String matchSpecification  = line.substring(0, specificationEnd).trim();
                    String iconName = line.substring(specificationEnd + 1, iconNameEnd).trim(); 
                    DecorationRuleMatcher matcher = MatcherFactory.INSTANCE.matcherOf(matchSpecification);
                    LinkDecorationRule rule = new LinkDecorationRule(matcher, iconName);
                    rules.add(rule);
                }
                else {
                    LogUtils.warn("Ignore link decoration rule " + line);
                }
			}
			iniFileLastModified = iniFileLastModified();
			Collections.sort(rules, Comparator.comparing(LinkDecorationRule::getMaximalScore).reversed());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private long iniFileLastModified() {
        File file = Compat.urlToFile(iniFile);
        return file != null ? file.lastModified() : 0;
    }

    private boolean isBlank(String line) {
		return line.length() == 0;
	}

	private boolean isComment(String line) {
		return line.startsWith("#");
	}

	private List<String> parseRegexes(String regexesString, LinkDecorationRule rule) {
		List<String> regexes = new ArrayList<String>();
		StringTokenizer tokenizer = new StringTokenizer(regexesString, ",");
		while (tokenizer.hasMoreTokens()) {
			String regex = tokenizer.nextToken().trim();
			regex = regex.substring(1, regex.length() - 1);
			regexes.add(regex);
		}
		return regexes;
	}
}
