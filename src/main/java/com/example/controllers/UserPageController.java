package com.example.controllers;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.model.Booking;
import com.example.model.DecorationStyle;
import com.example.model.Room;
import com.example.model.TimeSlot;
import com.example.model.User;
import com.example.services.BookingService;
import com.example.services.DecorationStyleService;
import com.example.services.RoomService;
import com.example.services.TimeSlotService;
import com.example.services.UserService;

@Controller
@RequestMapping("/user")
public class UserPageController {
    
    private final BookingService bookingService;
    private final UserService userService;
    private final RoomService roomService;
    private final TimeSlotService timeSlotService;
    private final DecorationStyleService decorationStyleService;
    
    @Autowired
    public UserPageController(BookingService bookingService, UserService userService,
                             RoomService roomService, TimeSlotService timeSlotService,
                             DecorationStyleService decorationStyleService) {
        this.bookingService = bookingService;
        this.userService = userService;
        this.roomService = roomService;
        this.timeSlotService = timeSlotService;
        this.decorationStyleService = decorationStyleService;
    }
    
    @GetMapping("/bookings")
    public String viewBookings(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        List<Booking> bookings = bookingService.getBookingsByUser(user);
        model.addAttribute("bookings", bookings);
        return "user/bookings";
    }
    
    @GetMapping("/select-decoration")
    public String selectDecoration(@RequestParam(required = false) Long timeSlotId,
                                 @RequestParam(required = false) Long roomId,
                                 @RequestParam(required = false) String bookingDate,
                                 Model model) {
        // If any required parameter is missing, redirect to the home page
        if (timeSlotId == null || roomId == null || bookingDate == null || bookingDate.isEmpty()) {
            return "redirect:/";
        }
        
        TimeSlot timeSlot = timeSlotService.getTimeSlotById(timeSlotId);
        Room room = roomService.getRoomById(roomId);
        
        if (timeSlot == null || room == null) {
            return "redirect:/";
        }
        
        List<DecorationStyle> decorationStyles = decorationStyleService.getAllDecorationStyles();
        
        model.addAttribute("timeSlot", timeSlot);
        model.addAttribute("room", room);
        model.addAttribute("bookingDate", bookingDate);
        model.addAttribute("decorationStyles", decorationStyles);
        return "user/select-decoration";
    }
    
    @PostMapping("/create-booking")
    public String createBooking(@RequestParam("timeSlotId") Long timeSlotId,
                              @RequestParam("roomId") Long roomId,
                              @RequestParam("decorationStyleId") Long decorationStyleId,
                              @RequestParam("bookingDate") String bookingDate,
                              HttpSession session, 
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        TimeSlot timeSlot = timeSlotService.getTimeSlotById(timeSlotId);
        Room room = roomService.getRoomById(roomId);
        DecorationStyle decorationStyle = decorationStyleService.getDecorationStyleById(decorationStyleId);
        
        if (timeSlot == null || room == null || decorationStyle == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi khi đặt phòng. Vui lòng thử lại.");
            return "redirect:/user/select-timeslot";
        }
        
        // Validate booking date and time
        if (!bookingService.isValidBookingTime(timeSlot, bookingDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Không thể đặt phòng cho thời gian trong quá khứ. Vui lòng chọn thời gian khác.");
            return "redirect:/";
        }
        
        // Create a new booking
        Booking booking = bookingService.createBooking(user, room, timeSlot, decorationStyle, bookingDate);
        
        if (booking != null) {
            // Set booking status to PENDING (waiting for payment)
            bookingService.updateBookingStatus(booking.getId(), "PENDING");
            // Redirect to payment page
            return "redirect:/user/payment/" + booking.getId();
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi khi đặt phòng. Vui lòng thử lại.");
            return "redirect:/user/select-timeslot";
        }
    }
    
    @GetMapping("/payment/{bookingId}")
    public String paymentPage(@PathVariable("bookingId") Long bookingId, Model model) {
        Booking booking = bookingService.getBookingById(bookingId);
        if (booking == null) {
            return "redirect:/user/bookings";
        }
        
        model.addAttribute("booking", booking);
        model.addAttribute("bankAccount", "1234567890");
        model.addAttribute("bankName", "VCB - Ngân hàng TMCP Ngoại thương Việt Nam");
        return "user/payment";
    }
    
    @PostMapping("/confirm-payment")
    public String confirmPayment(
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("paymentInfo") String paymentInfo,
            RedirectAttributes redirectAttributes) {
        bookingService.updateBookingPaymentInfo(bookingId, paymentInfo);
        redirectAttributes.addFlashAttribute("successMessage", "Thanh toán thành công! Đơn đặt phòng của bạn đang chờ xác nhận.");
        return "redirect:/user/bookings";
    }
    
    @GetMapping("/cancel-booking/{bookingId}")
    public String cancelBooking(@PathVariable("bookingId") Long bookingId, RedirectAttributes redirectAttributes) {
        bookingService.updateBookingStatus(bookingId, "CANCELLED_BY_USER");
        redirectAttributes.addFlashAttribute("successMessage", "Hủy đặt phòng thành công!");
        return "redirect:/user/bookings";
    }
}