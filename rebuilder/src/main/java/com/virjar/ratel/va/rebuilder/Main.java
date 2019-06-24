package com.virjar.ratel.va.rebuilder;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.Icon;
import net.dongliu.apk.parser.bean.IconFace;
import net.dongliu.apk.parser.struct.resource.Densities;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import brut.androlib.Androlib;
import brut.androlib.ApkDecoder;
import brut.androlib.ApkOptions;
import brut.androlib.res.xml.ResXmlPatcher;

public class Main {
    public static void main(String[] args) throws Exception {

        final Options options = new Options();
        options.addOption(new Option("k", "keep", false, "keep out apk package(not rename)"));
        options.addOption(new Option("n", "number", true, "number of output apks(0-20)"));
        options.addOption(new Option("o", "output", true, "the output apk list directory"));
        options.addOption(new Option("w", "workdir", true, "set a ratel working dir"));


        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption('w')) {
            theWorkDir = new File(cmd.getOptionValue('w'));
        }

        //工作目录准备
        File outFile;
        if (cmd.hasOption('o')) {
            outFile = new File(cmd.getOptionValue('o'));
        } else {
            outFile = new File("dist");
        }

        String[] apkParam = cmd.getArgs();

        if (apkParam.length == 0) {
            System.out.println("not target apk source ");
            return;
        }

        File file = new File(apkParam[0]);
        if (!file.exists() || !file.canRead()) {
            System.out.println("can not read target file: " + file.getAbsolutePath());
            return;
        }


        ApkFile apkFile = new ApkFile(file);

        if (apkFile.getApkMeta().getPackageName().contains(".ratel.va")) {
            System.out.println("assembled already???");
            return;
        }

        System.out.println("ratel build param: " + Joiner.on(" ").join(args));


        File workDir = cleanWorkDir();

        File templateApk = new File(workDir, "base.apk");
        System.out.println("release template apk ,into :" + templateApk.getAbsolutePath());
        IOUtils.copy(Main.class.getClassLoader().getResourceAsStream("base.apk"), new FileOutputStream(templateApk));


        File templateDir = new File(workDir, "template");
        System.out.println("decode template apk");
        ApkDecoder apkDecoder = new ApkDecoder();
        apkDecoder.setApkFile(templateApk);
        apkDecoder.setOutDir(templateDir);
        apkDecoder.decode();
        apkDecoder.close();


        File templateAssetsDir = new File(templateDir, "assets");
        //insert delegate apk
        FileUtils.copyFileToDirectory(file, templateAssetsDir);


        //replace logo
        replaceLogo(apkFile, templateDir);


        //rename label
        File resDir = new File(templateDir, "res");
        File[] valuesFiles = resDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.startsWith("values");
            }
        });
        if (valuesFiles != null) {
            for (File valueSFile : valuesFiles) {
                updateLabel(valueSFile, file);
            }
        }

        if (cmd.hasOption("k")) {
            //keep with delegate package
            //replace package
            String packageName = apkFile.getApkMeta().getPackageName();
            assemble(templateDir, outFile, packageName);
        } else if (cmd.hasOption("n")) {
            int n = NumberUtils.toInt(cmd.getOptionValue("n"), 10);
            for (int i = 0; i < n; i++) {
                String packageName = apkFile.getApkMeta().getPackageName() + ".ratel.va" + i;
                assemble(templateDir, outFile, packageName);
            }
        } else {
            assemble(templateDir, outFile, apkFile.getApkMeta().getPackageName() + ".ratel.va");
        }
    }

    private static void assemble(File templateDir, File outDir, String outPackageName) throws Exception {
        renamePackage(outPackageName, templateDir);
        ApkOptions apkOptions = new ApkOptions();
        apkOptions.forceBuildAll = true;

        File outFile = new File(outDir, outPackageName + "_" + new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date()) + ".apk");
        System.out.println("out put apk file: " + outFile.getAbsolutePath());
        new Androlib(apkOptions).build(templateDir, outFile);
    }


    private static void replaceLogo(ApkFile apkFile, File templateDir) throws Exception {

        ArrayList<Icon> icons = Lists.newArrayList(Iterables.transform(Iterables.filter(apkFile.getAllIcons(), new Predicate<IconFace>() {
            @Override
            public boolean apply(@Nullable IconFace input) {
                return input instanceof Icon;
            }


        }), new Function<IconFace, Icon>() {
            @Nullable
            @Override
            public Icon apply(@Nullable IconFace input) {
                return (Icon) input;
            }
        }));
        icons.sort(new Comparator<Icon>() {
            @Override
            public int compare(Icon icon, Icon t1) {
                return Integer.compare(icon.getDensity(), t1.getDensity());
            }
        });

        File resDir = new File(templateDir, "res");
        File[] mipmaps = resDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.startsWith("mipmap");
            }
        });

        if (mipmaps != null) {
            for (File mipMapFile : mipmaps) {
                Integer density = densityDefines.get(mipMapFile.getName());
                if (density == null) {
                    System.out.println("can not find density define for: " + mipMapFile.getName());
                    continue;
                }
                Icon hintedIcon = null;
                for (Icon icon : icons) {
                    if (icon.getDensity() >= density) {
                        hintedIcon = icon;
                        break;
                    }
                }
                if (hintedIcon == null) {
                    hintedIcon = icons.get(icons.size() - 1);
                }
                byte[] data = hintedIcon.getData();
                File launcherFile = new File(mipMapFile, "ic_launcher.png");
                if (data != null) {
                    FileUtils.writeByteArrayToFile(launcherFile, data);
                } else {
                    FileUtils.forceDelete(launcherFile);
                }
            }
        }
    }


    private static void renamePackage(String toPackage, File templateDir) throws Exception {
        File androidManifestXmlFile = new File(templateDir, "AndroidManifest.xml");
        Document document = ResXmlPatcher.loadDocument(androidManifestXmlFile);
        Element manifestEle = (Element) document.getElementsByTagName("manifest").item(0);
        String originPackage = manifestEle.getAttribute("package");
        manifestEle.setAttribute("package", toPackage);
        XPath xPath = XPathFactory.newInstance().newXPath();
        XPathExpression expression = xPath.compile("/manifest/application/provider");

        NodeList nodeList = (NodeList) expression.evaluate(document, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node item = nodeList.item(i);
            if (!(item instanceof Element)) {
                continue;
            }
            Element providerEl = (Element) item;
            String authorities = providerEl.getAttribute("android:authorities");
            if (authorities == null) {
                continue;
            }
            if (!authorities.startsWith(originPackage + ".")) {
                continue;
            }
            String newAuthorities = toPackage + authorities.substring(originPackage.length());
            providerEl.setAttribute("android:authorities", newAuthorities);
        }

        ResXmlPatcher.saveDocument(androidManifestXmlFile, document);


    }


    private static Map<String, Integer> densityDefines = new HashMap<>();

    static {
        densityDefines.put("mipmap-hdpi", Densities.HIGH);
        densityDefines.put("mipmap-mdpi", Densities.MEDIUM);
        densityDefines.put("mipmap-xhdpi", Densities.XHIGH);
        densityDefines.put("mipmap-xxhdpi", Densities.XXHIGH);
        densityDefines.put("mipmap-xxxhdpi", Densities.XXHIGH);
    }

    private static File theWorkDir = null;

    private static File workDir() {
        if (theWorkDir == null) {
            String home = System.getProperty("user.home");
            if (home != null) {
                theWorkDir = new File(home, ".ratel_work_dir_va");
            } else {
                theWorkDir = new File(".ratel_work_dir_va");
            }
            //        theWorkDir = new File("/Users/virjar/Desktop/temp");
        }
        return theWorkDir;
    }

    private static File cleanWorkDir() {
        File workDir = workDir();
        FileUtils.deleteQuietly(workDir);
        try {
            FileUtils.forceMkdir(workDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return workDir;
    }

    private static void updateLabel(File valueSDir, File delegateApk) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {
        File stringXmlFile = new File(valueSDir, "strings.xml");
        if (!stringXmlFile.exists()) {
            //not need to update
            return;
        }
        Document document = ResXmlPatcher.loadDocument(stringXmlFile);
        XPath xPath = XPathFactory.newInstance().newXPath();
        XPathExpression expression = xPath.compile("/resources/string[@name=\"app_name\"]");

        Object result = expression.evaluate(document, XPathConstants.NODE);
        if (result == null) {
            return;
        }

        String name = valueSDir.getName();
        Locale locale = Locale.US;
        int index = name.indexOf("-");
        if (index > 0) {
            String localString = name.substring(index + 1);
            String language = localString;
            String area = "";
            if (localString.contains("-")) {
                String[] split = localString.split("-");
                language = split[0];
                area = split[1].replaceAll("r", "");
            }
            try {
                locale = new Locale(language, area);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        String label = null;
        try (ApkFile apkFile = new ApkFile(delegateApk)) {
            apkFile.setPreferredLocale(locale);
            label = apkFile.getApkMeta().getLabel();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Element element = (Element) result;
        element.setTextContent(label);
        ResXmlPatcher.saveDocument(stringXmlFile, document);

    }
}
