package cz.muni.fi.pv168.jate.hotelreservationsystem.ui;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.optionalusertools.*;
import cz.muni.fi.pv168.jate.hotelreservationsystem.model.*;

import javax.swing.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.*;

final class NewReservationPanel {

    private final JPanel panel;
    private final Dashboard owner;
    private final RoomTypesPanel roomTypesPanel;
    private final DatePicker checkinDatePicker = new DatePicker(LocalDate.now(), null);
    private final DatePicker checkoutDatePicker = new DatePicker(LocalDate.now().plusDays(1), null);
    private Person reservationCreator = null;

    public NewReservationPanel(Dashboard owner) {
        this.owner = owner;
        panel = new JPanel(new GridBagLayout());
        panel.setName("New reservation");
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(15, 15, 15, 15);

        gbc.gridy = 0;
        panel.add(createDatePickers(), gbc);

        JButton createReservationButton = new JButton("Create reservation");
        roomTypesPanel = new RoomTypesPanel(createReservationButton);

        gbc.gridy++;
        panel.add(roomTypesPanel.getPanel(), gbc);

        createReservationButton.setEnabled(false);
        createReservationButton.addActionListener(e -> createReservations());

        gbc.gridy++;
        panel.add(createReservationButton, gbc);
    }

    public JPanel getPanel() {
        return panel;
    }

    private JPanel createDatePickers() {
        JPanel datePickerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(0, 3, 0, 3);

        datePickerPanel.add(createDatePickerField("Check-in date", checkinDatePicker, checkinDateChanged()), gbc);
        datePickerPanel.add(createDatePickerField("Check-out date", checkoutDatePicker, checkoutDateChanged()), gbc);

        return datePickerPanel;
    }

    private JPanel createDatePickerField(String label, DatePicker datePicker, DateChangeListener handler) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        panel.add(new JLabel(label + ":"), gbc);

        gbc.gridy++;
        gbc.ipady = 5;

        datePicker.addDateChangeListener(handler);
        panel.add(datePicker, gbc);

        return panel;
    }

    private DateChangeListener checkinDateChanged() {
        return dateChangeEvent -> {
            if (checkinDatePicker.getDate() != null) {
                checkoutDatePicker.disableUntil(checkinDatePicker.getDate().plusDays(1));
            }
            validateDatePickers();
        };
    }

    private DateChangeListener checkoutDateChanged() {
        return dateChangeEvent -> {
            if (checkoutDatePicker.getDate() != null) {
                checkinDatePicker.disableAfter(checkoutDatePicker.getDate().minusDays(1));
            }
            validateDatePickers();
        };
    }

    private void validateDatePickers() {
        roomTypesPanel.updateRoomTypeLines(getFreeRoomNumbers(
                checkinDatePicker.getDate(),
                checkoutDatePicker.getDate())
        );
    }

    private List<Long> getFreeRoomNumbers(LocalDate checkinDate, LocalDate checkoutDate) {
        List<Long> resultNumbers = new ArrayList<>();

        if (checkinDate == null || checkoutDate == null) {
            return resultNumbers;
        }

        List<Reservation> currentReservations = owner.getReservationDao().findAll();
        for (long roomNumber = 1; roomNumber <= 20; roomNumber++) {
            long currentRoomNumber = roomNumber;

            List<Reservation> reservationsForCurrentRoom = currentReservations.stream()
                    .filter(reservation -> reservation.getRoom().getId().equals(currentRoomNumber))
                    .collect(Collectors.toList());

            List<Reservation> NonCollidingReservationsForCurrentRoom = reservationsForCurrentRoom.stream()
                    .filter(reservation -> (
                            reservation.getCheckinDate().compareTo(checkinDate) < 0
                            && reservation.getCheckoutDate().compareTo(checkinDate) <= 0)

                            || reservation.getCheckinDate().compareTo(checkoutDate) >= 0)
                    .collect(Collectors.toList());

            if (NonCollidingReservationsForCurrentRoom.size() == reservationsForCurrentRoom.size()) {
                resultNumbers.add(roomNumber);
            }
        }
        return resultNumbers;
    }

    private void createReservations() {
        NewReservationDialog personInformation = new NewReservationDialog(owner);

        if (!personInformation.isConfirmed()) {
            return;
        }

        reservationCreator = createReservationOwner(personInformation);
        owner.getPersonDao().create(reservationCreator);

        List<Long> freeRoomNumbers = getFreeRoomNumbers(checkinDatePicker.getDate(), checkoutDatePicker.getDate());

        for (RoomType roomType : RoomType.values()) {
            int index = roomType.ordinal();

            createReservationsByRoomType(
                    roomTypesPanel.getCheckBoxes().get(index),
                    roomTypesPanel.getSpinners().get(index),
                    roomType,
                    freeRoomNumbers);
        }

        validateDatePickers();
    }

    private Person createReservationOwner(NewReservationDialog personInformation) {
        Person person = new Person(
                personInformation.getName(),
                personInformation.getSurname(),
                personInformation.getBirthDate(),
                personInformation.getEvidenceID()
        );
        person.setPhoneNumber(personInformation.getPhoneNumber());
        person.setEmail(personInformation.getEmail().isEmpty() ?
                null :
                personInformation.getEmail());

        return person;
    }

    private void createReservationsByRoomType(JCheckBox checkBox, JSpinner spinner, RoomType roomType, List<Long> freeRoomNumbers) {
        if (!checkBox.isSelected() || (int) spinner.getValue() == 0) {
            return;
        }

        List<Long> freeRoomNumbersByRoomType = freeRoomNumbers.stream()
                .filter(roomNumber -> RoomType.getType(roomNumber) == roomType)
                .collect(Collectors.toList());

        for (int i = 0; i < (int) spinner.getValue(); i++) {
            Room freeRoom = new Room(freeRoomNumbersByRoomType.get(i), roomType);
            createReservation(freeRoom, reservationCreator);
        }
    }

    private void createReservation(Room room, Person person) {
        Reservation reservation = new Reservation(
                person,
                room,
                checkinDatePicker.getDate(),
                checkoutDatePicker.getDate());

        owner.getReservationDao().create(reservation);
    }
}
