package validator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.smartgridready.ns.v0.*;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Unmarshaller;

public class CheckDeviceStructure {
    // TODO make paths configurable
    private static final String SPEC_PATH = "../SGrSpecifications/";
    private static final String SPEC_PACKAGE = "com.smartgridready.ns.v0";

    private static final String UNDEFINED_VALUE = "UNDEFINED";

    private static final HashMap<String, List<FunctionalProfileFrame>> funcProfiles = new HashMap<>();

    public static void main(String[] args) {
        try {
            // create a JAXBContext capable of handling classes generated from XSD
            JAXBContext jc = JAXBContext.newInstance(SPEC_PACKAGE);

            // create an Unmarshaller
            Unmarshaller u = jc.createUnmarshaller();

            // parse functional profile definitions
            for (File file : new File(SPEC_PATH + "XMLInstances/FuncProfiles").listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".xml");
                }
            })) {
                FunctionalProfileFrame fpElement = (FunctionalProfileFrame) u.unmarshal(file);

                String type = fpElement.getFunctionalProfile().getFunctionalProfileIdentification()
                        .getFunctionalProfileType();
                String category = (fpElement.getFunctionalProfile().getFunctionalProfileIdentification().getFunctionalProfileCategory() != null)
                        ? fpElement.getFunctionalProfile().getFunctionalProfileIdentification().getFunctionalProfileCategory().value()
                        : UNDEFINED_VALUE;
                String version = getVersionString(fpElement.getFunctionalProfile().getFunctionalProfileIdentification().getVersionNumber());
                String key = type + "@" + category;

                if (funcProfiles.get(key) == null) {
                    funcProfiles.put(key, new ArrayList<>());
                }

                funcProfiles.get(key).add(fpElement);

                System.out.println();
                System.out.println("FP: " + key + ", "
                        + ((fpElement.getFunctionalProfile().getFunctionalProfileIdentification().getLevelOfOperation() != null) ? fpElement.getFunctionalProfile().getFunctionalProfileIdentification().getLevelOfOperation().value() : UNDEFINED_VALUE) + ", "
                        + version + ", "
                        + ((fpElement.getReleaseNotes().getState() != null) ? fpElement.getReleaseNotes().getState().value() : UNDEFINED_VALUE) + ", "
                        + file.getName());
                System.out.flush();

                if (fpElement.getFunctionalProfile().getFunctionalProfileIdentification().getLevelOfOperation() == null) {
                    System.err.println("==> undefined FP level of operation");
                }
                if (fpElement.getReleaseNotes().getState() == null) {
                    System.err.println("==> undefined FP release state");
                }
            }

            System.out.println();
            System.out.println();
            System.out.flush();

            // parse EIDs
            for (File file : new File(SPEC_PATH + "XMLInstances/ExtInterfaces").listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".xml");
                }
            })) {
                @SuppressWarnings("unchecked")
                JAXBElement<DeviceFrame> jaxbElement = (JAXBElement<DeviceFrame>) u.unmarshal(file);

                DeviceFrame eidElement = (DeviceFrame) jaxbElement.getValue();

                String version = getVersionString(eidElement.getDeviceInformation().getVersionNumber());

                System.out.println();
                System.out.println("EID: " + eidElement.getDeviceName() + ", " + eidElement.getManufacturerName() + ", "
                        + ((eidElement.getDeviceInformation().getLevelOfOperation() != null) ? eidElement.getDeviceInformation().getLevelOfOperation().value() : UNDEFINED_VALUE) + ", "
                        + version + ", "
                        + ((eidElement.getReleaseNotes().getState() != null) ? eidElement.getReleaseNotes().getState().value() : UNDEFINED_VALUE) + ", "
                        + file.getName());
                System.out.flush();

                if (eidElement.getDeviceInformation().getLevelOfOperation() == null) {
                    System.err.println("==> undefined device level of operation");
                }
                if (eidElement.getReleaseNotes().getState() == null) {
                    System.err.println("==> undefined device release state");
                }

                List<FunctionalProfileBase> profiles = new ArrayList<>();

                if (eidElement.getInterfaceList().getRestApiInterface() != null) {
                    profiles.addAll(eidElement.getInterfaceList().getRestApiInterface().getFunctionalProfileList()
                            .getFunctionalProfileListElement());
                } else if (eidElement.getInterfaceList().getModbusInterface() != null) {
                    profiles.addAll(eidElement.getInterfaceList().getModbusInterface().getFunctionalProfileList()
                            .getFunctionalProfileListElement());
                } else if (eidElement.getInterfaceList().getContactInterface() != null) {
                    profiles.addAll(eidElement.getInterfaceList().getContactInterface().getFunctionalProfileList()
                            .getFunctionalProfileListElement());
                } else if (eidElement.getInterfaceList().getMessagingInterface() != null) {
                    profiles.addAll(eidElement.getInterfaceList().getMessagingInterface().getFunctionalProfileList()
                            .getFunctionalProfileListElement());
                } else if (eidElement.getInterfaceList().getGenericInterface() != null) {
                    profiles.addAll(eidElement.getInterfaceList().getGenericInterface().getFunctionalProfileList()
                            .getFunctionalProfileListElement());
                } else {
                    System.err.println("==> undefined interface type");
                }

                for (FunctionalProfileBase prof : profiles) {
                    String type = prof.getFunctionalProfile().getFunctionalProfileIdentification()
                            .getFunctionalProfileType();
                    String category = (prof.getFunctionalProfile().getFunctionalProfileIdentification().getFunctionalProfileCategory() != null)
                        ? prof.getFunctionalProfile().getFunctionalProfileIdentification().getFunctionalProfileCategory().value()
                        : UNDEFINED_VALUE;
                    String fpVersion = getVersionString(prof.getFunctionalProfile().getFunctionalProfileIdentification().getVersionNumber());
                    String key = type + "@" + category;

                    FunctionalProfileFrame ok = checkFunctionalProfile(prof, funcProfiles.get(key), true);

                    if (ok == null) {
                        FunctionalProfileFrame okVersion = checkFunctionalProfile(prof, funcProfiles.get(key), false);

                        if (okVersion == null) {
                            System.err.println("==> no matching FP for " + key);
                        } else {
                            System.err.println("==> no matching FP for " + key + " (at version " + fpVersion + ")");
                        }

                        ok = okVersion;
                    } else {
                        System.out.println("==> ok for " + key);
                        System.out.flush();
                    }

                    if (ok != null) {
                        checkDataPoints(prof, ok, key);
                    }
                }

                System.out.println("-->" + profiles.size());
                System.out.flush();
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
    }

    private static FunctionalProfileFrame checkFunctionalProfile(FunctionalProfileBase device,
            List<FunctionalProfileFrame> fps, boolean checkVersion) {
        FunctionalProfileIdentification devFpId = device.getFunctionalProfile().getFunctionalProfileIdentification();

        if (fps == null) {
            return null;
        }

        for (FunctionalProfileFrame fp : fps) {
            FunctionalProfileIdentification fpFpId = fp.getFunctionalProfile().getFunctionalProfileIdentification();

            if ((devFpId.getVersionNumber().getPrimaryVersionNumber() == fpFpId.getVersionNumber()
                    .getPrimaryVersionNumber()
                    && devFpId.getVersionNumber().getSecondaryVersionNumber() == fpFpId.getVersionNumber()
                            .getSecondaryVersionNumber()
                    && devFpId.getVersionNumber().getSubReleaseVersionNumber() == fpFpId.getVersionNumber()
                            .getSubReleaseVersionNumber()
                    || !checkVersion)
                    && devFpId.getLevelOfOperation().equals(devFpId.getLevelOfOperation())) {
                return fp;
            }
        }

        return null;
    }

    private static boolean checkDataPoints(FunctionalProfileBase device, FunctionalProfileFrame fp, String key) {
        List<FunctionalProfileDataPoint> fpList = fp.getDataPointList().getDataPointListElement();
        List<DataPointBase> devList;

        if (device instanceof RestApiFunctionalProfile) {
            devList = new ArrayList<>(((RestApiFunctionalProfile) device).getDataPointList().getDataPointListElement());
        } else if (device instanceof ModbusFunctionalProfile) {
            devList = new ArrayList<>(((ModbusFunctionalProfile) device).getDataPointList().getDataPointListElement());
        } else if (device instanceof ContactFunctionalProfile) {
            devList = new ArrayList<>(((ContactFunctionalProfile) device).getDataPointList().getDataPointListElement());
        } else if (device instanceof MessagingFunctionalProfile) {
            devList = new ArrayList<>(
                    ((MessagingFunctionalProfile) device).getDataPointList().getDataPointListElement());
        } else if (device instanceof GenericFunctionalProfile) {
            devList = new ArrayList<>(((GenericFunctionalProfile) device).getDataPointList().getDataPointListElement());
        } else {
            devList = new ArrayList<>();
        }

        for (FunctionalProfileDataPoint fpElem : fpList) {
            String fpName = fpElem.getDataPoint().getDataPointName();
            boolean found = false;

            for (DataPointBase devElem : devList) {
                String devName = devElem.getDataPoint().getDataPointName();

                if (devName.equals(fpName)) {
                    boolean failed = false;

                    switch (devElem.getDataPoint().getDataDirection()) {
                        case C:
                        case R:
                            failed = fpElem.getDataPoint().getDataDirection() != DataDirectionFunctionalProfile.R;
                            break;

                        case RW:
                        case RWP:
                            failed = fpElem.getDataPoint().getDataDirection() != DataDirectionFunctionalProfile.RW;
                            break;

                        case W:
                            failed = fpElem.getDataPoint().getDataDirection() != DataDirectionFunctionalProfile.W;
                            break;
                    }

                    if (failed) {
                        System.err.println("    nok as inconsistent data directions dev:"
                                + devElem.getDataPoint().getDataDirection()
                                + ", fp:" + fpElem.getDataPoint().getDataDirection() + " for name:" + fpName + " - in "
                                + key);
                        return false;
                    }

                    String devDataType = getDataType(devElem.getDataPoint().getDataType());
                    String fpDataType = getDataType(fpElem.getDataPoint().getDataType());

                    if (!devDataType.equals(fpDataType)) {
                        System.err.println("    nok as inconsistent data types dev:" + devDataType
                                + ", fp:" + fpDataType + " for name:" + fpName + " - in " + key);
                        return false;
                    }

                    if (devElem.getDataPoint().getUnit() != fpElem.getDataPoint().getUnit()) {
                        System.err.println("    nok as inconsistent unit dev:" + devElem.getDataPoint().getUnit()
                                + ", fp:" + fpElem.getDataPoint().getUnit() + " for name:" + fpName + " - in " + key);
                        return false;
                    }

                    if (found) {
                        System.err.println("    nok as two data points with same name:" + fpName + " - in " + key);
                        return false;
                    }

                    found = true;
                }
            }

            if (!found && fpElem.getDataPoint().getPresenceLevel() == PresenceLevel.M) {
                System.err.println("    nok as " + fpName + " is mandatory");
                return false;
            }
        }

        return true;
    }

    private static String getDataType(DataTypeProduct type) {
        if (type.getBitmap() != null) {
            return "bitmap";
        }

        if (type.getEnum() != null) {
            return "enum";
        }

        if (type.getJson() != null) {
            return "json";
        }

        if (type.getBoolean() != null) {
            return "boolean";
        }

        if (type.getInt8() != null) {
            return "int8";
        }

        if (type.getInt16() != null) {
            return "int16";
        }

        if (type.getInt32() != null) {
            return "int32";
        }

        if (type.getInt64() != null) {
            return "int64";
        }

        if (type.getInt8U() != null) {
            return "int8U";
        }

        if (type.getInt16U() != null) {
            return "int16U";
        }

        if (type.getInt32U() != null) {
            return "int32U";
        }

        if (type.getInt64U() != null) {
            return "int64U";
        }

        if (type.getFloat32() != null) {
            return "float32";
        }

        if (type.getFloat64() != null) {
            return "float64";
        }

        if (type.getDateTime() != null) {
            return "dateTime";
        }

        if (type.getString() != null) {
            return "string";
        }

        return "?";
    }

    private static String getDataType(DataTypeFunctionalProfile type) {
        if (type.getBitmap() != null) {
            return "bitmap";
        }

        if (type.getEnum() != null) {
            return "enum";
        }

        if (type.getJson() != null) {
            return "json";
        }

        if (type.getBoolean() != null) {
            return "boolean";
        }

        if (type.getInt8() != null) {
            return "int8";
        }

        if (type.getInt16() != null) {
            return "int16";
        }

        if (type.getInt32() != null) {
            return "int32";
        }

        if (type.getInt64() != null) {
            return "int64";
        }

        if (type.getInt8U() != null) {
            return "int8U";
        }

        if (type.getInt16U() != null) {
            return "int16U";
        }

        if (type.getInt32U() != null) {
            return "int32U";
        }

        if (type.getInt64U() != null) {
            return "int64U";
        }

        if (type.getFloat32() != null) {
            return "float32";
        }

        if (type.getFloat64() != null) {
            return "float64";
        }

        if (type.getDateTime() != null) {
            return "dateTime";
        }

        if (type.getString() != null) {
            return "string";
        }

        return "?";
    }

    private static String getVersionString(VersionNumber v) {
        return (v != null)
            ? String.join(".", String.valueOf(v.getPrimaryVersionNumber()), String.valueOf(v.getSecondaryVersionNumber()), String.valueOf(v.getSubReleaseVersionNumber()))
            : UNDEFINED_VALUE;
    }
}
