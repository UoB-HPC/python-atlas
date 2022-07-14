const {merge} = require('webpack-merge');
const generated = require('./scalajs.webpack.config');
const path = require('path');

var local = {
    devtool: false,
    performance: {
        // See https://github.com/scalacenter/scalajs-bundler/pull/408
        // and also https://github.com/scalacenter/scalajs-bundler/issues/350
        hints: false
    },
    module: {
        rules: [
            {
                test: /\.css$/,
                use: ['style-loader', 'css-loader'],
                type: 'javascript/auto',
            },
            {
                test: /\.(eot|ttf|woff(2)?|svg|png|glb|jpeg|jpg|mp4|jsn)$/,
                type: 'asset/resource',
                generator: {
                    filename: 'static/[hash][ext][query]'
                }
                // use: 'file-loader',
            }
        ]
    }
};

module.exports = merge(generated, local);