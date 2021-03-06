/**
 * <h1>ImplementationCheckerLoader.java</h1> <p> This program is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version; or,
 * at your choice, under the terms of the Mozilla Public License, v. 2.0. SPDX GPL-3.0+ or MPL-2.0+.
 * </p> <p> This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License and the Mozilla Public License for more details. </p>
 * <p> You should have received a copy of the GNU General Public License and the Mozilla Public
 * License along with this program. If not, see <a href="http://www.gnu.org/licenses/">http://www.gnu.org/licenses/</a>
 * and at <a href="http://mozilla.org/MPL/2.0">http://mozilla.org/MPL/2.0</a> . </p> <p> NB: for the
 * © statement, include Easy Innova SL or other company/Person contributing the code. </p> <p> ©
 * 2015 Easy Innova, SL </p>
 */


package com.easyinnova.implementation_checker;

import com.easyinnova.implementation_checker.rules.model.ImplementationCheckerObjectType;
import com.easyinnova.implementation_checker.rules.model.IncludeType;
import com.easyinnova.implementation_checker.rules.model.RuleType;
import com.easyinnova.implementation_checker.rules.model.RulesType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 * Created by Adria Llorens on 19/10/2016.
 */
public class ImplementationCheckerLoader {

  static HashMap<String, ImplementationCheckerObjectType> preLoadedValidatorsSingleton = new HashMap<>();

  private static List<String> isoNames;
  private static List<String> isoPaths;

  public static String getDefaultIso() {
    return "TIFF_Baseline_Core_6_0";
  }

  public synchronized static ImplementationCheckerObjectType getRules(String rulesFile) {
    if (!rulesFile.contains("/") && !rulesFile.contains(".")) {
      rulesFile = "implementationcheckers/" + rulesFile + ".xml";
    }

    ImplementationCheckerObjectType rules = null;
    try {
      if (!preLoadedValidatorsSingleton.containsKey(rulesFile)) {
        JAXBContext jaxbContext = JAXBContext.newInstance(ImplementationCheckerObjectType.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        try {
          rules = (ImplementationCheckerObjectType) jaxbUnmarshaller.unmarshal(getFileFromResources(rulesFile));
        } catch (Exception ex) {
          ex.printStackTrace();
        }

        for (RulesType ro : rules.getRules()) {
          for (RuleType rule : ro.getRule()) {
            //rule.iso = rules.getIso();
          }
        }

        if (rules.getInclude() != null) {
          for (IncludeType inc : rules.getInclude()) {
            JAXBContext jaxbContextInc = JAXBContext.newInstance(ImplementationCheckerObjectType.class);
            Unmarshaller jaxbUnmarshallerInc = jaxbContextInc.createUnmarshaller();
            ImplementationCheckerObjectType rulesIncluded = (ImplementationCheckerObjectType) jaxbUnmarshallerInc.unmarshal(getFileFromResources("implementationcheckers/" + inc.getPolicyChecker()));

            for (RulesType ro : rulesIncluded.getRules()) {
              boolean excludedRules = false;
              HashSet<String> excluded = new HashSet<>();
              for (String id : inc.getExcluderules()) {
                if (id.equals(ro.getId())) excludedRules = true;
                excluded.add(id);
              }
              if (!excludedRules) {
                ro.setIncluded(true);
                rules.getRules().add(ro);
                for (int i = 0; i < ro.getRule().size(); i++) {
                  if (excluded.contains(ro.getRule().get(i).getId())) {
                    ro.getRule().remove(i--);
                  }
                }
                for (RuleType rule : ro.getRule()) {
                  //rule.iso = rulesIncluded.getIso();
                }
              }
            }
          }
        }

        preLoadedValidatorsSingleton.put(rulesFile, rules);
      } else {
        rules = preLoadedValidatorsSingleton.get(rulesFile);
      }
    } catch (JAXBException ex) {
      return null;
    }
    return rules;
  }

  private static InputStream getFileFromResources(String spathStr) {
    String pathStr = spathStr.replace("./", "");
    InputStream fis = null;
    File file = new File("src/main/resources/" + pathStr);
    try {
      if (file.exists()) {
        // Look in current dir
        fis = new FileInputStream("src/main/resources/" + pathStr);
      } else {
        // Look in class
        Class cls = ImplementationCheckerLoader.class;
        ClassLoader cLoader = cls.getClassLoader();
        fis = cLoader.getResourceAsStream(pathStr);
        fis = null;

        if (fis == null) {
          // Look in JAR
          CodeSource src = ImplementationCheckerLoader.class.getProtectionDomain().getCodeSource();
          if (src != null) {
            try {
              URL jar = src.getLocation();
              ZipInputStream zip = new ZipInputStream(jar.openStream());
              ZipEntry zipFile;
              while ((zipFile = zip.getNextEntry()) != null) {
                String name = zipFile.getName();
                if (name.contains(pathStr)) {
                  try {
                    fis = zip;
                    break;
                  } catch (Exception ex) {
                    throw new Exception("");
                  }
                }
              }
            } catch (Exception ex) {

            }
          }
        }
      }
    } catch (FileNotFoundException e) {
    }
    return fis;
  }

  public static List<String> getPathsList() {
    if (isoPaths == null) {
      List<String> list = new ArrayList<>();
      String path = "implementationcheckers";

      // Read from classpath
      try {
        Class cls = ImplementationCheckerLoader.class;
        ClassLoader cLoader = cls.getClassLoader();
        InputStream in = cLoader.getResourceAsStream(path);

        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String resource;
        while ((resource = br.readLine()) != null) {
          if (resource.endsWith(".xml")) {
            list.add(path + "/" + resource);
          }
        }
      } catch (Exception e) {
        list = new ArrayList<>();
      }

      if (list.isEmpty()) {
        // Read from jar
        try {
          CodeSource src = ImplementationCheckerLoader.class.getProtectionDomain().getCodeSource();
          if (src != null) {
            URL jar = src.getLocation();
            ZipInputStream zip = new ZipInputStream(jar.openStream());
            ZipEntry zipFile;
            while ((zipFile = zip.getNextEntry()) != null) {
              String name = zipFile.getName();
              if (name.endsWith(".xml") && name.startsWith("implementationcheckers")) {
                list.add(name);
              }
            }
          }
        } catch (Exception ex) {
          list = new ArrayList<>();
        }
      }
      isoPaths = list;
    }
    return isoPaths;
  }

  public static List<String> getNamesList() {
    if (isoNames == null) {
      List<String> list = new ArrayList<>();
      String path = "implementationcheckers";
      try {
        Class cls = ImplementationCheckerLoader.class;
        ClassLoader cLoader = cls.getClassLoader();
        InputStream in = cLoader.getResourceAsStream(path);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String resource;
        while ((resource = br.readLine()) != null) {
          if (resource.endsWith(".xml")) {
            list.add(resource.replace(".xml", ""));
          }
        }
      } catch (Exception e) {

      }
      isoNames = list;
    }
    return isoNames;
  }

  public static String getFileName(String path) {
    if (path.contains("/") && path.contains(".")) {
      return path.substring(path.indexOf("/") + 1, path.indexOf("."));
    }
    return path;
  }

  public static String getIsoName(String path) {
    ImplementationCheckerObjectType icRules = ImplementationCheckerLoader.getRules(path);
    if (icRules != null) {
      return icRules.getTitle();
    }
    return getFileName(path);
  }

  public static boolean isValid(String path) {
    ImplementationCheckerObjectType icRules = ImplementationCheckerLoader.getRules(path);
    return icRules != null;
  }

}
