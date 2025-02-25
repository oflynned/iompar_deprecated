package com.glassbyte.iompar;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by ed on 28/10/15.
 */
public class Sync {

    Context context;
    boolean loaded;
    String title, nextDue, arrivalInfo, chosenEndStation, depart, arrive;
    Realtime.LuasDirections lineDirection;

    ArrayList<String> endDestinationList = new ArrayList<>();
    ArrayList<String> waitingTimeList = new ArrayList<>();

    Fares fares = new Fares();

    Fares.FareType fareClass;
    Fares.FarePayment farePayment;

    public Sync(Context context) {
        this.context = context;
    }

    public String requestUpdate(Globals.LineDirection direction,
                                String depart,
                                String arrive) throws Exception {
        setLoaded(false);
        threadConnect(direction, depart, arrive, context);
        return getDepartures();
    }

    /**
     * Connects to the RTPI URL with the respective stations given and returns the appropriate
     * scraped values for the depart station to destination
     *
     * @param direction direction in which the user is travelling
     * @param depart    departure station in string format where the user is leaving from
     * @param arrive    name of station at which the user is arriving
     */
    public void threadConnect(final Globals.LineDirection direction, final String depart,
                              final String arrive, final Context parametrisedContext) {
        final Globals globals = new Globals(parametrisedContext);
        Thread downloadThread = new Thread() {
            public void run() {
                setLoaded(false);
                Document doc;
                try {
                    URL url = new URL(Globals.RTPI_LUAS + globals.getLuasStation(depart));
                    doc = Jsoup.connect(url.toString()).get();

                    System.out.println("got URL " + url);

                    //green line
                    if (direction.equals(Globals.LineDirection.stephens_green_to_brides_glen) ||
                            direction.equals(Globals.LineDirection.stephens_green_to_sandyford)) {
                        if (stationBeforeSandyford(arrive, globals.greenLineBeforeSandyford)) {
                            System.out.println("towards Sandyford/Bride's Glen");
                            scrapeData(doc, "Sandyford", "Brides Glen", depart, arrive);
                        } else {
                            System.out.println("towards Bride's Glen");
                            scrapeData(doc, "Brides Glen", depart, arrive);
                        }
                    } else if (direction.equals(Globals.LineDirection.sandyford_to_stephens_green) ||
                            direction.equals(Globals.LineDirection.brides_glen_to_stephens_green)) {
                        System.out.println("towards stephen's green");
                        scrapeData(doc, "St. Stephen's Green", depart, arrive);
                    }

                    //red line - tallaght/point
                    else if (direction.equals(Globals.LineDirection.belgard_to_tallaght) ||
                            direction.equals(Globals.LineDirection.the_point_to_tallaght)) {
                        System.out.println("towards tallaght");
                        scrapeData(doc, "Tallaght", depart, arrive);
                    } else if (direction.equals(Globals.LineDirection.belgard_to_the_point) ||
                            direction.equals(Globals.LineDirection.tallaght_to_the_point) ||
                            direction.equals(Globals.LineDirection.tallaght_to_belgard)) {
                        System.out.println("towards the point");
                        scrapeData(doc, "The Point", depart, arrive);
                    } else if (direction.equals(Globals.LineDirection.the_point_to_belgard)) {
                        System.out.println("towards tallaght");
                        scrapeData(doc, "Tallaght", depart, arrive);
                    }

                    //saggart/connolly
                    else if (direction.equals(Globals.LineDirection.belgard_to_saggart)) {
                        System.out.println("towards saggart");
                        scrapeData(doc, "Saggart", depart, arrive);
                    } else if (direction.equals(Globals.LineDirection.saggart_to_belgard)) {
                        System.out.println("towards belgard");
                        scrapeData(doc, "The Point", "Belgard", "Connolly", depart, arrive);
                    } else if (direction.equals(Globals.LineDirection.belgard_to_connolly)) {
                        System.out.println("towards the point");
                        scrapeData(doc, "Connolly", depart, arrive);
                    } else if (direction.equals(Globals.LineDirection.connolly_to_belgard)) {
                        System.out.println("towards belgard");
                        scrapeData(doc, "Saggart", depart, arrive);
                    }

                    //connolly/heuston
                    else if (direction.equals(Globals.LineDirection.heuston_to_connolly)) {
                        System.out.println("towards Connolly");
                        scrapeData(doc, "Connolly", "The Point", depart, arrive);
                    } else if (direction.equals(Globals.LineDirection.connolly_to_heuston)) {
                        System.out.println("towards Heuston from town");
                        scrapeData(doc, "Heuston", "Saggart", depart, arrive);
                    }

                    System.out.println(nextDue);
                } catch (IOException e) {
                    setNextDue(globals.getString(R.string.could_not_connect_real_time));
                    setArrivalInfo(globals.getString(R.string.sometimes_rtpi_is_down));
                    e.printStackTrace();
                }
                setLoaded(true);
            }
        };
        downloadThread.start();
        setLoaded(false);
    }


    /**
     * scrapes the data from the HTML RTPI website given the start and end stations
     * for the given line.
     *
     * @param doc        document to be scraped
     * @param endStation station the user travels towards
     * @param depart     station the user is coming from
     * @param arrive     station the user is going to
     */
    public void scrapeData(Document doc, String endStation, String depart, String arrive) {
        Elements location = doc.getElementsByClass("location");
        Elements time = doc.getElementsByClass("time");

        String[] RTPI = new String[location.size() * 2];

        for (int i = 0; i < location.size(); i++) {
            RTPI[i * 2] = location.get(i).text();
            RTPI[(i * 2) + 1] = time.get(i).text();
        }

        endDestinationList.clear();
        waitingTimeList.clear();

        if (location.toString().contains("No trams forecast") && location.size() == 1) {
            setNextDue(context.getString(R.string.no_departures_found));
            setArrivalInfo(context.getString(R.string.no_times_found));
        } else {
            for (int j = 0; j < RTPI.length; j = j + 2) {
                if (RTPI[j].equals(endStation)) {
                    setChosenEndStation(endStation);
                    endDestinationList.add(RTPI[j]);
                    waitingTimeList.add(RTPI[j + 1]);
                    System.out.println(RTPI[j]);
                    System.out.println(RTPI[j + 1]);
                } else {
                    setNextDue(context.getString(R.string.unavailable_try_other_line));
                    setArrivalInfo(context.getString(R.string.unavailable_try_other_line));
                }
            }

            if (!endDestinationList.isEmpty()) {
                setNextDue(
                        context.getString(R.string.origin) + "\n" + depart + "\n" +
                                context.getString(R.string.destination) + "\n" + arrive);
                setArrivalInfo(
                        context.getString(R.string.terminus) + "\n" + convertEndStation(String.valueOf(endDestinationList.get(0))) + "\n" +
                                context.getString(R.string.eta) + getTimeFormat(String.valueOf(waitingTimeList.get(0))) + "\n" +
                                context.getString(R.string.cost) + ": €" + fares.getZoneTraversal(convertStringToEnum(
                                getChosenEndStation()), depart, arrive, context, "default"));

                //accessing via dialogs
                setEnumDirection(convertStringToEnum(getChosenEndStation()));
                System.out.println(getEnumDirection());
                setDepart(depart);
                setArrive(arrive);

                setFareClass(fares.getFareType());
                setFarePayment(fares.getFarePayment());
            }
        }
    }

    public void scrapeData(Document doc, String endStation, String endStationAlternate,
                           String depart, String arrive) {
        Elements location = doc.getElementsByClass("location");
        Elements time = doc.getElementsByClass("time");

        String[] RTPI = new String[location.size() * 2];

        for (int i = 0; i < location.size(); i++) {
            RTPI[i * 2] = location.get(i).text();
            RTPI[(i * 2) + 1] = time.get(i).text();
        }

        endDestinationList.clear();
        waitingTimeList.clear();

        if (location.toString().contains("No trams forecast") && location.size() == 1) {
            setNextDue(context.getString(R.string.no_departures_found));
            setArrivalInfo(context.getString(R.string.no_times_found));
        } else {
            for (int j = 0; j < RTPI.length; j = j + 2) {
                if (RTPI[j].equals(endStation)) {
                    setChosenEndStation(endStation);
                    endDestinationList.add(RTPI[j]);
                    waitingTimeList.add(RTPI[j + 1]);
                    System.out.println(RTPI[j]);
                    System.out.println(RTPI[j + 1]);
                } else if (RTPI[j].equals(endStationAlternate)) {
                    setChosenEndStation(endStationAlternate);
                    endDestinationList.add(RTPI[j]);
                    waitingTimeList.add(RTPI[j + 1]);
                    System.out.println(RTPI[j]);
                    System.out.println(RTPI[j + 1]);
                } else {
                    setNextDue(context.getString(R.string.unavailable_try_other_line));
                    setArrivalInfo(context.getString(R.string.unavailable_try_other_line));
                }
            }
        }

        if (!endDestinationList.isEmpty()) {
            setNextDue(
                    context.getString(R.string.origin) + "\n" + depart + "\n" +
                            context.getString(R.string.destination) + "\n" + arrive);
            setArrivalInfo(
                    context.getString(R.string.terminus) + "\n" + convertEndStation(String.valueOf(endDestinationList.get(0))) + "\n" +
                            context.getString(R.string.eta) + getTimeFormat(String.valueOf(waitingTimeList.get(0))) + "\n" +
                            context.getString(R.string.cost) + ": €" + fares.getZoneTraversal(convertStringToEnum(
                            getChosenEndStation()), depart, arrive, context, "default"));

            //accessing via dialogs
            setEnumDirection(convertStringToEnum(getChosenEndStation()));
            System.out.println(getEnumDirection());
            setDepart(depart);
            setArrive(arrive);

            setFareClass(fares.getFareType());
            setFarePayment(fares.getFarePayment());
        }
    }


    public void scrapeData(Document doc, String endStation, String endStationAlternate,
                           String endStationSecondAlternate, String depart, String arrive) {
        Elements location = doc.getElementsByClass("location");
        Elements time = doc.getElementsByClass("time");

        String[] RTPI = new String[location.size() * 2];

        for (int i = 0; i < location.size(); i++) {
            RTPI[i * 2] = location.get(i).text();
            RTPI[(i * 2) + 1] = time.get(i).text();
        }

        endDestinationList.clear();
        waitingTimeList.clear();

        if (location.toString().contains("No trams forecast") && location.size() == 1) {
            setNextDue(context.getString(R.string.no_departures_found));
            setArrivalInfo(context.getString(R.string.no_times_found));
        } else {
            for (int j = 0; j < RTPI.length; j = j + 2) {
                if (RTPI[j].equals(endStation)) {
                    setChosenEndStation(endStation);
                    endDestinationList.add(RTPI[j]);
                    waitingTimeList.add(RTPI[j + 1]);
                    System.out.println(RTPI[j]);
                    System.out.println(RTPI[j + 1]);
                } else if (RTPI[j].equals(endStationAlternate)) {
                    setChosenEndStation(endStationAlternate);
                    endDestinationList.add(RTPI[j]);
                    waitingTimeList.add(RTPI[j + 1]);
                    System.out.println(RTPI[j]);
                    System.out.println(RTPI[j + 1]);
                } else if (RTPI[j].equals(endStationSecondAlternate)) {
                    setChosenEndStation(endStationSecondAlternate);
                    endDestinationList.add(RTPI[j]);
                    waitingTimeList.add(RTPI[j + 1]);
                    System.out.println(RTPI[j]);
                    System.out.println(RTPI[j + 1]);
                } else {
                    setNextDue(context.getString(R.string.unavailable_try_other_line));
                    setArrivalInfo(context.getString(R.string.unavailable_try_other_line));
                }
            }
        }

        if (!endDestinationList.isEmpty()) {
            setNextDue(
                    context.getString(R.string.origin) + ": " + "\n" + depart + "\n" +
                            context.getString(R.string.destination) + ": " + "\n" + arrive);
            setArrivalInfo(
                    context.getString(R.string.terminus) + "\n" + convertEndStation(String.valueOf(endDestinationList.get(0))) + "\n" +
                            context.getString(R.string.eta) + getTimeFormat(String.valueOf(waitingTimeList.get(0))) + "\n" +
                            context.getString(R.string.cost) + ": €" + fares.getZoneTraversal(convertStringToEnum(
                            getChosenEndStation()), depart, arrive, context, "default"));

            //accessing via dialogs
            setEnumDirection(convertStringToEnum(getChosenEndStation()));
            System.out.println(getChosenEndStation());
            System.out.println(getEnumDirection());
            setDepart(depart);
            setArrive(arrive);

            setFareClass(fares.getFareType());
            setFarePayment(fares.getFarePayment());
        }
    }

    public Realtime.LuasDirections convertStringToEnum(String endStation) {
        System.out.println(endStation);
        if (endStation.equals("Tallaght") || endStation.equals("Tamhlacht") || endStation.equals(context.getString(R.string.tallaght))) {
            return Realtime.LuasDirections.TALLAGHT;
        } else if (endStation.equals("Saggart") || endStation.equals("Teach Sagard") || endStation.equals(context.getString(R.string.saggart))) {
            return Realtime.LuasDirections.SAGGART;
        } else if (endStation.equals("Connolly") || endStation.equals("Stáisiúin Uí Chonghaile") || endStation.equals(context.getString(R.string.connolly))) {
            return Realtime.LuasDirections.CONNOLLY;
        } else if (endStation.equals("The Point") || endStation.equals("Iosta na Rinne") || endStation.equals(context.getString(R.string.the_point))) {
            return Realtime.LuasDirections.POINT;
        } else if (endStation.equals("St. Stephen's Green") || endStation.equals("Faiche Stiabhna") || endStation.equals(context.getString(R.string.stephens_green))) {
            return Realtime.LuasDirections.STEPHENS_GREEN;
        } else if (endStation.equals("Sandyford") || endStation.equals("Áth an Ghainimh") || endStation.equals(context.getString(R.string.sandyford))) {
            return Realtime.LuasDirections.SANDYFORD;
        } else if (endStation.equals("Brides Glen") || endStation.equals("Gleann Bhríde") || endStation.equals(context.getString(R.string.brides_glen))) {
            return Realtime.LuasDirections.BRIDES_GLEN;
        } else if (endStation.equals("Heuston") || endStation.equals("Heuston") || endStation.equals(context.getString(R.string.heuston))) {
            return Realtime.LuasDirections.HEUSTON;
        } else {
            return null;
        }
    }

    public String convertEndStation(String endStation) {
        switch (endStation) {
            case "Tallaght":
                return context.getString(R.string.tallaght);
            case "Saggart":
                return context.getString(R.string.saggart);
            case "Connolly":
                return context.getString(R.string.connolly);
            case "The Point":
                return context.getString(R.string.the_point);
            case "Heuston":
                return context.getString(R.string.heuston);
            case "Sandyford":
                return context.getString(R.string.sandyford);
            case "Brides Glen":
                return context.getString(R.string.brides_glen);
            case "St. Stephen's Green":
                return context.getString(R.string.stephens_green);
            case "Belgard":
                return context.getString(R.string.belgard);
        }
        return null;
    }

    public String getTimeFormat(String time) {
        switch (time) {
            case "Unavailable":
                return context.getString(R.string.unavailable);
            case "DUE":
                return context.getString(R.string.arriving_soon);
            case "1":
                return context.getString(R.string.one_min_away);
            default:
                return time.replaceAll("[^0-9]", "") + " " + context.getString(R.string.mins_away);
        }
    }

    public static boolean stationBeforeSandyford(String arrive, String[] items) {
        for (String item : items) {
            if (arrive.contains(item)) {
                return true;
            }
        }
        return false;
    }

    public String getDepartures() {
        return title;
    }

    public void setNextDue(String nextDue) {
        this.nextDue = nextDue;
    }

    public String getNextDue() {
        return nextDue;
    }

    public void setArrivalInfo(String arrivalInfo) {
        this.arrivalInfo = arrivalInfo;
    }

    public String getArrivalInfo() {
        return arrivalInfo;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setChosenEndStation(String chosenEndStation) {
        this.chosenEndStation = chosenEndStation;
    }

    public String getChosenEndStation() {
        return chosenEndStation;
    }

    public void setFareClass(Fares.FareType fareType) {
        this.fareClass = fareType;
    }

    public Fares.FareType getFareClass() {
        return fareClass;
    }

    public void setFarePayment(Fares.FarePayment farePayment) {
        this.farePayment = farePayment;
    }

    public Fares.FarePayment getFarePayment() {
        return farePayment;
    }

    public void setEnumDirection(Realtime.LuasDirections lineDirection) {
        this.lineDirection = lineDirection;
    }

    public Realtime.LuasDirections getEnumDirection() {
        return lineDirection;
    }

    public void setDepart(String depart) {
        this.depart = depart;
    }

    public String getDepart() {
        return depart;
    }

    public void setArrive(String arrive) {
        this.arrive = arrive;
    }

    public String getArrive() {
        return arrive;
    }
}
