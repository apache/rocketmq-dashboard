import React, {useState, useEffect} from 'react';
import logo from './logo.svg';
import './App.css';

function App() {
    const [message, setMessage] = useState("");

    useEffect(() => {
        fetch('cluster/list.query')
            .then(response => response.text())
            .then(message => {
                setMessage(message);
            });
    }, [])


    return (
        <div className="App">
            <header className="App-header">
                <img src={logo} className="App-logo" alt="logo" height="60"/>
                <p>
                    Edit <code>src/App.js</code> and save to reload.
                </p>
            </header>
            <h1>ClusterInfo</h1>
            <p>
                {message}
            </p>
        </div>
    );
}

export default App;
