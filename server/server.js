const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    maxHttpBufferSize: 1e7 // Increase to 10MB for video frames
});

const PORT = 3000;

let volunteerSocket = null;
let blindSocket = null;

io.on('connection', (socket) => {
    console.log('User connected:', socket.id);

    socket.on('join_volunteer', () => {
        volunteerSocket = socket.id;
        console.log('Volunteer joined:', socket.id);
        socket.join('volunteer_room');
        if (blindSocket) {
            io.to(volunteerSocket).emit('blind_online');
        }
    });

    socket.on('join_blind', () => {
        blindSocket = socket.id;
        console.log('Blind user joined:', socket.id);
        socket.join('blind_room');
        if (volunteerSocket) {
            io.to(volunteerSocket).emit('blind_online');
            io.to(blindSocket).emit('volunteer_online');
        }
    });

    socket.on('video_frame', (data) => {
        // Forward video frame from blind user to volunteer
        socket.to('volunteer_room').emit('video_frame', data);
    });

    socket.on('audio_chunk', (data) => {
        // Forward audio between parties
        if (socket.id === blindSocket) {
            socket.to('volunteer_room').emit('audio_chunk', data);
        } else {
            socket.to('blind_room').emit('audio_chunk', data);
        }
    });

    socket.on('disconnect', () => {
        if (socket.id === volunteerSocket) {
            volunteerSocket = null;
            console.log('Volunteer disconnected');
            io.to('blind_room').emit('volunteer_offline');
        } else if (socket.id === blindSocket) {
            blindSocket = null;
            console.log('Blind user disconnected');
            io.to('volunteer_room').emit('blind_offline');
        }
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`VisionAssist Socket Server running on port ${PORT}`);
});
