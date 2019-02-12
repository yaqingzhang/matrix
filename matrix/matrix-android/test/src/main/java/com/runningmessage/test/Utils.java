package com.runningmessage.test;


import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Lorss on 19-1-23.
 */
public class Utils {


    private static void formatMethodCountTask(JsonObject jsonObject, JsonObject config) {
        JsonArray groups = null;
        if (config != null) {
            groups = config.getAsJsonArray("group");
        }
        //entries
        Map<String, Integer> defMethodMap = new HashMap<>();
        Map<String, Integer> refMethodMap = new HashMap<>();

        JsonArray dexFiles = jsonObject.getAsJsonArray("dex-files");
        for (JsonElement entry : dexFiles) {
            JsonObject dexFile = entry.getAsJsonObject();

            JsonArray defGroups = null;
            if (dexFile.has("internal-packages")) {
                defGroups = dexFile.getAsJsonArray("internal-packages");
            } else if (dexFile.has("internal-classes")) {
                defGroups = dexFile.getAsJsonArray("internal-classes");
            }
            if (defGroups != null) {
                for (JsonElement group : defGroups) {
                    JsonObject groupObj = group.getAsJsonObject();
                    String name = groupObj.get("name").getAsString();
                    defMethodMap.put(name, groupObj.get("methods").getAsInt());
                    if (!refMethodMap.containsKey(name)) {
                        refMethodMap.put(name, 0);
                    }
                }
            }
            JsonArray refGroups = null;
            if (dexFile.has("external-packages")) {
                refGroups = dexFile.getAsJsonArray("external-packages");
            } else if (dexFile.has("external-classes")) {
                refGroups = dexFile.getAsJsonArray("external-classes");
            }
            if (refGroups != null) {
                for (JsonElement group : refGroups) {
                    JsonObject groupObj = group.getAsJsonObject();
                    String name = groupObj.get("name").getAsString();
                    if (!refMethodMap.containsKey(name)) {
                        refMethodMap.put(name, groupObj.get("methods").getAsInt());
                    } else {
                        refMethodMap.put(name, refMethodMap.get(name) + groupObj.get("methods").getAsInt());
                    }
                    if (!defMethodMap.containsKey(name)) {
                        defMethodMap.put(name, 0);
                    }
                }
            }
        }

        final Map<String, Pair<Integer, Integer>> groupMap = new HashMap<>();
        final Map<String, Map<String, Pair<Integer, Integer>>> subGroupMaps = new HashMap<>();
        Map<String, String> packageToName = new HashMap<>();

        if (groups != null) {
            Map<String, String> groupPattern = new HashMap<>();

            ArrayList<String> allPackages = new ArrayList<>();
            for (JsonElement group : groups) {
                JsonObject obj = group.getAsJsonObject();
                String packageName = obj.get("package").getAsString();
                groupPattern.put(obj.get("name").getAsString(), packageName);
                if (!allPackages.contains(packageName)) {
                    allPackages.add(packageName);
                }
            }

            ArrayList<Map<String, String>> groupPatternsList = new ArrayList<>();
            StringBuilder packageTmp = new StringBuilder();
            for (Map.Entry<String, String> p : groupPattern.entrySet()) {


                String name = p.getKey();
                String packageName = p.getValue();
                String[] packageNameSplits = packageName.split("\\.");
                int packageDepth = 0;
                packageTmp.delete(0, packageTmp.length());


                for (int i = 0; i < packageNameSplits.length - 1; i++) {

                    if (i != 0) packageTmp.append(".");
                    packageTmp.append(packageNameSplits[i]);
                    String packageParent = packageTmp.toString();
                    for (String allPackagesItem : allPackages) {
                        if (allPackagesItem.equals(packageParent)) {
                            packageDepth++;
                            break;
                        } else {
                            int index = allPackagesItem.indexOf("$");

                            if (index > 0) {
                                String fixItem = allPackagesItem.substring(0, index);
                                if (packageParent.startsWith(fixItem)) {
                                    packageDepth++;
                                    break;
                                }
                            }
                        }
                    }
                }

                Map<String, String> groupPatternsDepth = null;
                int groupPatternsListSize = groupPatternsList.size();
                if (packageDepth < groupPatternsListSize) {
                    groupPatternsDepth = groupPatternsList.get(packageDepth);
                }
                if (groupPatternsDepth == null) {
                    groupPatternsDepth = new HashMap<>();
                    if (packageDepth + 1 > groupPatternsListSize) {
                        for (int i = 0; i < packageDepth + 1 - groupPatternsListSize; i++) {
                            groupPatternsList.add(new HashMap<String, String>());
                        }
                    }

                    groupPatternsList.set(packageDepth, groupPatternsDepth);
                } else {
                }
                groupPatternsDepth.put(name, packageName);
            }

            Map<String, Integer> groupDefMap = new HashMap<>();
            Map<String, Integer> groupRefMap = new HashMap<>();
            Map<String, Map<String, Integer>> subGroupDefMap = new HashMap<>();
            Map<String, Map<String, Integer>> subGroupRefMap = new HashMap<>();


            groupDefMap.put("[others]", 0);
            groupRefMap.put("[others]", 0);

            for (String pkg : defMethodMap.keySet()) {
                boolean other = true;

                for (int depth = 0; depth < groupPatternsList.size(); depth++) {

                    Map<String, String> groupPattens = groupPatternsList.get(depth);

                    for (String key : groupPattens.keySet()) {
                        String groupValue = groupPattens.get(key);
                        String groupName = key;
                        String packageKey = groupValue;

                        int index = groupValue.indexOf('$');
                        if (index >= 0) {
                            groupValue = groupValue.substring(0, index);
                        }

                        if (pkg.startsWith(groupValue)) {

                            if (index >= 0) {
                                groupName = pkg.substring(index);
                                int nextIndex = groupName.indexOf('.');
                                if (nextIndex >= 0) {
                                    groupName = groupName.substring(0, nextIndex);
                                }
                                packageKey = packageKey.replace("$", groupName);
                                groupName = key.replace("$", groupName);

                                allPackages.add(packageKey);

                            }


                            if (depth == 0) {
                                if (!groupDefMap.containsKey(packageKey)) {
                                    groupDefMap.put(packageKey, defMethodMap.get(pkg));
                                } else {
                                    groupDefMap.put(packageKey, groupDefMap.get(packageKey) + defMethodMap.get(pkg));
                                }

                                packageToName.put(packageKey, groupName);

                            } else {

                                StringBuilder sbPre = new StringBuilder();
                                for (int i = 0; i < depth; i++) {
                                    sbPre.append(" - ");
                                }
                                String pre = sbPre.toString();

                                Map<String, Integer> groupToFind = null;


                                String[] packageNameSplits = packageKey.split("\\.");
                                String packageParent;

                                for (int i = packageNameSplits.length - 1; i >= 0; i--) {

                                    packageTmp.delete(0, packageTmp.length());
                                    for (int j = 0; j < i; j++) {
                                        if (j != 0) packageTmp.append(".");
                                        packageTmp.append(packageNameSplits[j]);
                                    }
                                    packageParent = packageTmp.toString();
                                    if (allPackages.contains(packageParent)) {
                                        groupToFind = subGroupDefMap.get(packageParent);
                                        if (groupToFind == null) {
                                            groupToFind = new HashMap<>();
                                            subGroupDefMap.put(packageParent, groupToFind);
                                        }
                                        break;
                                    }

                                }


                                groupName = pre + groupName;
                                if (groupToFind != null) {
                                    if (!groupToFind.containsKey(packageKey)) {
                                        groupToFind.put(packageKey, defMethodMap.get(pkg));
                                    } else {
                                        groupToFind.put(packageKey, groupToFind.get(packageKey) + defMethodMap.get(pkg));
                                    }
                                }
                                packageToName.put(packageKey, groupName);
                            }
                            other = false;
                        } else {
                        }
                    }
                }

                if (other) {
                    groupDefMap.put("[others]", groupDefMap.get("[others]") + defMethodMap.get(pkg));
                }


            }

            for (String pkg : refMethodMap.keySet()) {
                boolean other = true;


                for (int depth = 0; depth < groupPatternsList.size(); depth++) {

                    Map<String, String> groupPattens = groupPatternsList.get(depth);

                    for (String key : groupPattens.keySet()) {
                        String groupValue = groupPattens.get(key);
                        String groupName = key;
                        String packageKey = groupValue;

                        int index = groupValue.indexOf('$');
                        if (index >= 0) {
                            groupValue = groupValue.substring(0, index);
                        }

                        if (pkg.startsWith(groupValue)) {

                            if (index >= 0) {
                                groupName = pkg.substring(index);
                                int nextIndex = groupName.indexOf('.');
                                if (nextIndex >= 0) {
                                    groupName = groupName.substring(0, nextIndex);
                                }
                                packageKey = groupValue.replace("$", groupName);
                                groupName = key.replace("$", groupName);

                                allPackages.add(packageKey);
                            }


                            if (depth == 0) {
                                if (!groupRefMap.containsKey(packageKey)) {
                                    groupRefMap.put(packageKey, refMethodMap.get(pkg));
                                } else {
                                    groupRefMap.put(packageKey, groupRefMap.get(packageKey) + refMethodMap.get(pkg));
                                }

                                packageToName.put(packageKey, groupName);

                            } else {

                                StringBuilder sbPre = new StringBuilder();
                                for (int i = 0; i < depth; i++) {
                                    sbPre.append(" - ");
                                }
                                String pre = sbPre.toString();

                                Map<String, Integer> groupToFind = null;


                                String[] packageNameSplits = packageKey.split("\\.");
                                String packageParent;

                                for (int i = packageNameSplits.length - 1; i >= 0; i--) {

                                    packageTmp.delete(0, packageTmp.length());
                                    for (int j = 0; j < i; j++) {
                                        if (j != 0) packageTmp.append(".");
                                        packageTmp.append(packageNameSplits[j]);
                                    }
                                    packageParent = packageTmp.toString();
                                    if (allPackages.contains(packageParent)) {
                                        groupToFind = subGroupRefMap.get(packageParent);
                                        if (groupToFind == null) {
                                            groupToFind = new HashMap<>();
                                            subGroupRefMap.put(packageParent, groupToFind);
                                        }
                                        break;
                                    }

                                }


                                groupName = pre + groupName;
                                if (groupToFind != null) {
                                    if (!groupToFind.containsKey(packageKey)) {
                                        groupToFind.put(packageKey, refMethodMap.get(pkg));
                                    } else {
                                        groupToFind.put(packageKey, groupToFind.get(packageKey) + refMethodMap.get(pkg));
                                    }
                                }
                                packageToName.put(packageKey, groupName);

                            }
                            other = false;
                        }
                    }
                }
                if (other) {
                    groupRefMap.put("[others]", groupRefMap.get("[others]") + refMethodMap.get(pkg));
                }
            }

            for (String pkg : groupDefMap.keySet()) {
                groupMap.put(pkg, Pair.of(groupDefMap.get(pkg), groupRefMap.get(pkg)));
            }

            for (String pkg : subGroupDefMap.keySet()) {
                Map<String, Integer> def = subGroupDefMap.get(pkg);
                Map<String, Integer> ref = subGroupRefMap.get(pkg);
                Map<String, Pair<Integer, Integer>> gm = new HashMap<>();
                subGroupMaps.put(pkg, gm);

                if (def != null) {
                    for (String pkgItem : def.keySet()) {
                        Integer first = def.get(pkgItem);
                        if (first == null) first = 0;

                        Integer second = 0;
                        if (ref != null) {
                            second = ref.get(pkgItem);
                        }
                        if (second == null) second = 0;

                        gm.put(pkgItem, Pair.of(first, second));
                    }
                } else if (ref != null) {
                    for (String pkgItem : ref.keySet()) {

                        Integer second = ref.get(pkgItem);
                        if (second == null) second = 0;

                        gm.put(pkgItem, Pair.of(0, second));
                    }
                }
            }

        } else {
            for (String pkg : defMethodMap.keySet()) {
                groupMap.put(pkg, Pair.of(defMethodMap.get(pkg), refMethodMap.get(pkg)));
            }
        }
        ArrayList<String> keys = new ArrayList<>(groupMap.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                Pair<Integer, Integer> pair1 = groupMap.get(left);
                Pair<Integer, Integer> pair2 = groupMap.get(right);
                int total1 = (pair1.getFirst() != null ? pair1.getFirst() : 0)
                        + (pair1.getSecond() != null ? pair1.getSecond() : 0);
                int total2 = (pair2.getFirst() != null ? pair2.getFirst() : 0)
                        + (pair2.getSecond() != null ? pair2.getSecond() : 0);

                if (total1 > total2) {
                    return -1;
                } else if (total1 < total2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        jsonObject.remove("dex-files");

        long totalMethods = 0;
        JsonArray groupArray = new JsonArray();
        for (String group : keys) {
            JsonObject groupObj = new JsonObject();
            groupObj.addProperty("name", packageToName.get(group));
            Pair<Integer, Integer> pair = groupMap.get(group);
            totalMethods += (pair.getFirst() != null ? pair.getFirst() : 0)
                    + (pair.getSecond() != null ? pair.getSecond() : 0);
            groupObj.addProperty("method-count", (pair.getFirst() != null ? pair.getFirst() : 0)
                    + (pair.getSecond() != null ? pair.getSecond() : 0));
            groupArray.add(groupObj);

            fillChildrenToJsonArray(subGroupMaps, group, groupArray, packageToName);
        }

        jsonObject.addProperty("total-methods", totalMethods);
        jsonObject.add("groups", groupArray);
    }

    private static void fillChildrenToJsonArray(
            Map<String, Map<String, Pair<Integer, Integer>>> subGroupMaps,
            String group, JsonArray groupArray,
            Map<String, String> packageToName) {
        final Map<String, Pair<Integer, Integer>> subGroupMap = subGroupMaps.get(group);
        if (subGroupMap != null && subGroupMap.size() > 0) {
            ArrayList<String> subGroupMapKeys = new ArrayList<>(subGroupMap.keySet());
            Collections.sort(subGroupMapKeys, new Comparator<String>() {
                @Override
                public int compare(String left, String right) {
                    Pair<Integer, Integer> pair1 = subGroupMap.get(left);
                    Pair<Integer, Integer> pair2 = subGroupMap.get(right);
                    int total1 = (pair1.getFirst() != null ? pair1.getFirst() : 0)
                            + (pair1.getSecond() != null ? pair1.getSecond() : 0);
                    int total2 = (pair2.getFirst() != null ? pair2.getFirst() : 0)
                            + (pair2.getSecond() != null ? pair2.getSecond() : 0);


                    if (total1 > total2) {
                        return -1;
                    } else if (total1 < total2) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
            for (String subGroup : subGroupMapKeys) {
                JsonObject subGroupObj = new JsonObject();
                subGroupObj.addProperty("name", packageToName.get(subGroup));
                Pair<Integer, Integer> subPair = subGroupMap.get(subGroup);
                subGroupObj.addProperty("method-count", (subPair.getFirst() != null ? subPair.getFirst() : 0)
                        + (subPair.getSecond() != null ? subPair.getSecond() : 0));
                groupArray.add(subGroupObj);
                fillChildrenToJsonArray(subGroupMaps, subGroup, groupArray, packageToName);
            }
        }
    }

    static String config = "{\"name\": \"-countMethod\", \"group\": [{\"name\": \"System Android Support (android.support.$)\", \"package\": \"android.support.$\"}, {\"name\": \"System Android (android)\", \"package\": \"android\"}, {\"name\": \"System Java (java)\", \"package\": \"java\"}, {\"name\": \"News$ (com.qihoo360.news$)\", \"package\": \"com.qihoo360.news$\"}, {\"name\": \"Newssdk (com.qihoo360.newssdk.$)\", \"package\": \"com.qihoo360.newssdk.$\"}, {\"name\": \"Newssdk (com.qihoo360.newssdk.view.$)\", \"package\": \"com.qihoo360.newssdk.view.$\"}, {\"name\": \"Newssdk (com.qihoo360.newssdk.page.$)\", \"package\": \"com.qihoo360.newssdk.page.$\"}, {\"name\": \"Newssdk (com.qihoo360.newssdk.ui.$)\", \"package\": \"com.qihoo360.newssdk.ui.$\"}, {\"name\": \"Newssdkad (com.qihoo360.apullsdk)\", \"package\": \"com.qihoo360.apullsdk\"}, {\"name\": \"Newssdkad (com.ak)\", \"package\": \"com.ak\"}, {\"name\": \"Qihoo Web Sdk (com.qihoo.webview/webkit)\", \"package\": \"com.qihoo.web\"}, {\"name\": \"Qihoo Plugin Sdk (com.qihoo.browpf)\", \"package\": \"com.qihoo.browpf\"}, {\"name\": \"Tencent $ (com.tencent.$)\", \"package\": \"com.tencent.$\"}, {\"name\": \"Kotlin (kotlin)\", \"package\": \"kotlin\"}, {\"name\": \"Kotlin (kotlin.$)\", \"package\": \"kotlin.$\"}, {\"name\": \"Kotlinx Coroutines (kotlinx.coroutines)\", \"package\": \"kotlinx.coroutines\"}, {\"name\": \"Kotlinx Coroutines (kotlinx.coroutines.$)\", \"package\": \"kotlinx.coroutines.$\"}]}\n";
    static String obj = "{\"taskType\": 4, \"taskDescription\": \"Count methods in dex file, output results group by class name or package name.\", \"dex-files\": [{\"dex-file\": \"classes22.dex\", \"internal-packages\": [{\"name\": \"com.qihoo360.newssdk.utils\", \"methods\": 2}, {\"name\": \"com.qihoo360.newssdk.ppppp\", \"methods\": 5}, {\"name\": \"com.qihoo360.newssdk.view.i\", \"methods\": 5}, {\"name\": \"com.qihoo360.newssdk.view.j\", \"methods\": 5}, {\"name\": \"com.qihoo360.newssdk.view.k\", \"methods\": 5}], \"total-internal-classes\": 1, \"total-internal-methods\": 2, \"external-packages\": [], \"total-external-classes\": 5, \"total-external-methods\": 8}], \"start-time\": \"2019-01-23 16:38:41:076\", \"end-time\": \"2019-01-23 16:38:42:504\"}\n";

    public static void call(Context context) {

        try {
            JsonParser parser = new JsonParser();
            formatMethodCountTask((JsonObject) parser.parse(obj), (JsonObject) parser.parse(config));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
